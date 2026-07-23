#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <ctime>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

static VulkanComputeEngine& engine = VulkanComputeEngine::getInstance();

// ---------- JNI Entry Point ----------
extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_simulateInk(
        JNIEnv *env,
        jobject /* this */,
        jobject paperBitmap,
        jobject inkBitmap,
        jint offsetX,
        jint offsetY) {

    // 1. Ensure engine is alive
    if (!engine.initialize()) return;

    // 2. Lock bitmaps
    AndroidBitmapInfo paperInfo, inkInfo;
    void *paperPixels, *inkPixels;
    if (AndroidBitmap_getInfo(env, paperBitmap, &paperInfo) < 0) return;
    if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) return;
    if (AndroidBitmap_lockPixels(env, paperBitmap, &paperPixels) < 0) return;
    if (AndroidBitmap_lockPixels(env, inkBitmap, &inkPixels) < 0) {
        AndroidBitmap_unlockPixels(env, paperBitmap);
        return;
    }

    // 3. Setup dimensions
    int fullWidth = paperInfo.width;
    int fullHeight = paperInfo.height;
    setVulkanDevice(engine.getDevice());

    // 4. Upload paper bitmap to a Vulkan image (sampleable + transfer dst)
    VkImage paperImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                            VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
    if (!paperImage) goto cleanup;
    if (!engine.allocateImageMemory(paperImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    if (!engine.uploadImageData(paperImage, paperPixels, fullWidth, fullHeight)) {
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    VkImageView paperView = engine.createImageView(paperImage, VK_FORMAT_R8G8B8A8_UNORM);
    if (!paperView) {
        engine.destroyImage(paperImage);
        goto cleanup;
    }

    // 5. Upload ink stamp
    int stampW = inkInfo.width;
    int stampH = inkInfo.height;
    VkImage stampImage = engine.createImage(stampW, stampH, VK_FORMAT_R8G8B8A8_UNORM,
                                            VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
    if (!stampImage) {
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    if (!engine.allocateImageMemory(stampImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    if (!engine.uploadImageData(stampImage, inkPixels, stampW, stampH)) {
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    VkImageView stampView = engine.createImageView(stampImage, VK_FORMAT_R8G8B8A8_UNORM);
    if (!stampView) {
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }

    // 6. Create output image (storage)
    VkImage outputImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                             VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
    if (!outputImage) {
        engine.destroyImageView(stampView);
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    if (!engine.allocateImageMemory(outputImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
        engine.destroyImage(outputImage);
        engine.destroyImageView(stampView);
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }
    VkImageView outputView = engine.createImageView(outputImage, VK_FORMAT_R8G8B8A8_UNORM);
    if (!outputView) {
        engine.destroyImage(outputImage);
        engine.destroyImageView(stampView);
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }

    // 7. Get blend shader module
    VkShaderModule shaderModule = getInkBlendShaderModule();   // you'll need to load this
    if (!shaderModule) {
        engine.destroyImageView(outputView);
        engine.destroyImage(outputImage);
        engine.destroyImageView(stampView);
        engine.destroyImage(stampImage);
        engine.destroyImageView(paperView);
        engine.destroyImage(paperImage);
        goto cleanup;
    }

    // 8. Build descriptor set (2 samplers + 1 storage image)
    // We need a custom descriptor set layout for this shader.
    // Let's define it locally and allocate a set.
    VkDescriptorSetLayout blendLayout;
    {
        VkDescriptorSetLayoutBinding bindings[3] = {};
        bindings[0].binding = 0; bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; bindings[0].descriptorCount = 1; bindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        bindings[1].binding = 1; bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; bindings[1].descriptorCount = 1; bindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        bindings[2].binding = 2; bindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE; bindings[2].descriptorCount = 1; bindings[2].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        VkDescriptorSetLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        layoutInfo.bindingCount = 3; layoutInfo.pBindings = bindings;
        if (vkCreateDescriptorSetLayout(engine.getDevice(), &layoutInfo, nullptr, &blendLayout) != VK_SUCCESS) {
            engine.destroyImageView(outputView);
            engine.destroyImage(outputImage);
            engine.destroyImageView(stampView);
            engine.destroyImage(stampImage);
            engine.destroyImageView(paperView);
            engine.destroyImage(paperImage);
            goto cleanup;
        }
    }

    VkDescriptorSet descSet;
    {
        VkDescriptorSetAllocateInfo alloc{};
        alloc.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        alloc.descriptorPool = engine.getDescriptorPool();
        alloc.descriptorSetCount = 1;
        alloc.pSetLayouts = &blendLayout;
        if (vkAllocateDescriptorSets(engine.getDevice(), &alloc, &descSet) != VK_SUCCESS) {
            vkDestroyDescriptorSetLayout(engine.getDevice(), blendLayout, nullptr);
            engine.destroyImageView(outputView);
            engine.destroyImage(outputImage);
            engine.destroyImageView(stampView);
            engine.destroyImage(stampImage);
            engine.destroyImageView(paperView);
            engine.destroyImage(paperImage);
            goto cleanup;
        }
    }

    // Create sampler (we need one for the samplers in the descriptor)
    VkSampler sampler;
    {
        VkSamplerCreateInfo samplerInfo{};
        samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
        samplerInfo.magFilter = VK_FILTER_LINEAR;
        samplerInfo.minFilter = VK_FILTER_LINEAR;
        samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        if (vkCreateSampler(engine.getDevice(), &samplerInfo, nullptr, &sampler) != VK_SUCCESS) {
            vkFreeDescriptorSets(engine.getDevice(), engine.getDescriptorPool(), 1, &descSet);
            vkDestroyDescriptorSetLayout(engine.getDevice(), blendLayout, nullptr);
            engine.destroyImageView(outputView);
            engine.destroyImage(outputImage);
            engine.destroyImageView(stampView);
            engine.destroyImage(stampImage);
            engine.destroyImageView(paperView);
            engine.destroyImage(paperImage);
            goto cleanup;
        }
    }

    // Update descriptor set
    VkDescriptorImageInfo paperInfo{};
    paperInfo.imageView = paperView;
    paperInfo.sampler = sampler;
    paperInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkDescriptorImageInfo stampInfo{};
    stampInfo.imageView = stampView;
    stampInfo.sampler = sampler;
    stampInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkDescriptorImageInfo outInfo{};
    outInfo.imageView = outputView;
    outInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkWriteDescriptorSet writes[3] = {};
    writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[0].dstSet = descSet; writes[0].dstBinding = 0; writes[0].descriptorCount = 1; writes[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; writes[0].pImageInfo = &paperInfo;
    writes[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[1].dstSet = descSet; writes[1].dstBinding = 1; writes[1].descriptorCount = 1; writes[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; writes[1].pImageInfo = &stampInfo;
    writes[2].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[2].dstSet = descSet; writes[2].dstBinding = 2; writes[2].descriptorCount = 1; writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE; writes[2].pImageInfo = &outInfo;
    vkUpdateDescriptorSets(engine.getDevice(), 3, writes, 0, nullptr);

    // 9. Transition paper and stamp images to SHADER_READ_ONLY_OPTIMAL (they are in GENERAL after upload)
    {
        auto transition = [&](VkImage img) {
            VkCommandBuffer cmd;
            VkCommandBufferAllocateInfo alloc{};
            alloc.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
            alloc.commandPool = engine.getCommandPool();
            alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
            alloc.commandBufferCount = 1;
            vkAllocateCommandBuffers(engine.getDevice(), &alloc, &cmd);

            VkCommandBufferBeginInfo begin{};
            begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
            begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
            vkBeginCommandBuffer(cmd, &begin);

            VkImageMemoryBarrier barrier{};
            barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.image = img;
            barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
            barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
            vkEndCommandBuffer(cmd);

            VkSubmitInfo submit{};
            submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
            submit.commandBufferCount = 1;
            submit.pCommandBuffers = &cmd;
            vkQueueSubmit(engine.getComputeQueue(), 1, &submit, VK_NULL_HANDLE);
            vkQueueWaitIdle(engine.getComputeQueue());
            vkFreeCommandBuffers(engine.getDevice(), engine.getCommandPool(), 1, &cmd);
        };
        transition(paperImage);
        transition(stampImage);
    }

    // 10. Push constants
    struct BlendPush {
        float imageSize[2];
        int32_t stampOffset[2];
        int32_t stampSize[2];
    } pc;
    pc.imageSize[0] = (float)fullWidth;
    pc.imageSize[1] = (float)fullHeight;
    pc.stampOffset[0] = offsetX;
    pc.stampOffset[1] = offsetY;
    pc.stampSize[0] = stampW;
    pc.stampSize[1] = stampH;

    // 11. Dispatch blend shader
    bool dispatched = engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc),
                                             (fullWidth + 15) / 16, (fullHeight + 15) / 16, 1);
    if (dispatched) {
        // 12. Read back result into paper bitmap
        engine.copyImageToHost(outputImage, paperPixels, fullWidth, fullHeight);
    }

    // 13. Cleanup
    vkFreeDescriptorSets(engine.getDevice(), engine.getDescriptorPool(), 1, &descSet);
    vkDestroyDescriptorSetLayout(engine.getDevice(), blendLayout, nullptr);
    vkDestroySampler(engine.getDevice(), sampler, nullptr);
    engine.destroyImageView(outputView);
    engine.destroyImage(outputImage);
    engine.destroyImageView(stampView);
    engine.destroyImage(stampImage);
    engine.destroyImageView(paperView);
    engine.destroyImage(paperImage);

cleanup:
    AndroidBitmap_unlockPixels(env, paperBitmap);
    AndroidBitmap_unlockPixels(env, inkBitmap);
}