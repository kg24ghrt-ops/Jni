#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <ctime>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

static VulkanComputeEngine& engine = VulkanComputeEngine::getInstance();

extern "C"
JNIEXPORT void JNICALL
Java_com_example_homecil_PaperRenderer_simulateInk(
        JNIEnv *env,
        jobject /* this */,
        jobject paperBitmap,
        jobject inkBitmap,
        jint offsetX,
        jint offsetY) {

    // All Vulkan resources we might create – initialised to NULL so cleanup is safe
    VkImage paperImage = VK_NULL_HANDLE;
    VkImageView paperView = VK_NULL_HANDLE;
    VkImage stampImage = VK_NULL_HANDLE;
    VkImageView stampView = VK_NULL_HANDLE;
    VkImage outputImage = VK_NULL_HANDLE;
    VkImageView outputView = VK_NULL_HANDLE;
    VkDescriptorSetLayout blendLayout = VK_NULL_HANDLE;
    VkDescriptorSet descSet = VK_NULL_HANDLE;
    VkSampler sampler = VK_NULL_HANDLE;

    AndroidBitmapInfo paperInfo, inkInfo;
    void *paperPixels = nullptr, *inkPixels = nullptr;

    // Use a one-shot block so we can break out cleanly on any error
    do {
        // 1. Engine must be alive
        if (!engine.initialize()) break;

        // 2. Lock bitmaps
        if (AndroidBitmap_getInfo(env, paperBitmap, &paperInfo) < 0) break;
        if (AndroidBitmap_getInfo(env, inkBitmap, &inkInfo) < 0) break;
        if (AndroidBitmap_lockPixels(env, paperBitmap, &paperPixels) < 0) {
            paperPixels = nullptr;
            break;
        }
        if (AndroidBitmap_lockPixels(env, inkBitmap, &inkPixels) < 0) {
            inkPixels = nullptr;
            break;
        }

        int fullWidth = paperInfo.width;
        int fullHeight = paperInfo.height;
        int stampW = inkInfo.width;
        int stampH = inkInfo.height;

        setVulkanDevice(engine.getDevice());

        // 3. Upload paper
        paperImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                        VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        if (!paperImage) break;
        if (!engine.allocateImageMemory(paperImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) break;
        if (!engine.uploadImageData(paperImage, paperPixels, fullWidth, fullHeight)) break;

        paperView = engine.createImageView(paperImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!paperView) break;

        // 4. Upload ink stamp
        stampImage = engine.createImage(stampW, stampH, VK_FORMAT_R8G8B8A8_UNORM,
                                        VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        if (!stampImage) break;
        if (!engine.allocateImageMemory(stampImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) break;
        if (!engine.uploadImageData(stampImage, inkPixels, stampW, stampH)) break;

        stampView = engine.createImageView(stampImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!stampView) break;

        // 5. Create output image
        outputImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                         VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
        if (!outputImage) break;
        if (!engine.allocateImageMemory(outputImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) break;

        outputView = engine.createImageView(outputImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!outputView) break;

        // 6. Blend shader and descriptor layout
        VkShaderModule shaderModule = getInkBlendShaderModule();   // you'll need this in shader_loader
        if (!shaderModule) break;

        VkDescriptorSetLayoutBinding bindings[3] = {};
        bindings[0].binding = 0; bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; bindings[0].descriptorCount = 1; bindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        bindings[1].binding = 1; bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; bindings[1].descriptorCount = 1; bindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        bindings[2].binding = 2; bindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE; bindings[2].descriptorCount = 1; bindings[2].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

        VkDescriptorSetLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        layoutInfo.bindingCount = 3; layoutInfo.pBindings = bindings;
        if (vkCreateDescriptorSetLayout(engine.getDevice(), &layoutInfo, nullptr, &blendLayout) != VK_SUCCESS)
            break;

        // Allocate descriptor set
        VkDescriptorSetAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        allocInfo.descriptorPool = engine.getDescriptorPool();
        allocInfo.descriptorSetCount = 1;
        allocInfo.pSetLayouts = &blendLayout;
        if (vkAllocateDescriptorSets(engine.getDevice(), &allocInfo, &descSet) != VK_SUCCESS)
            break;

        // Create sampler
        VkSamplerCreateInfo samplerInfo{};
        samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
        samplerInfo.magFilter = VK_FILTER_LINEAR;
        samplerInfo.minFilter = VK_FILTER_LINEAR;
        samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        if (vkCreateSampler(engine.getDevice(), &samplerInfo, nullptr, &sampler) != VK_SUCCESS)
            break;

        // Write descriptor set
        VkDescriptorImageInfo paperInfoDesc{};
        paperInfoDesc.imageView = paperView;
        paperInfoDesc.sampler = sampler;
        paperInfoDesc.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkDescriptorImageInfo stampInfoDesc{};
        stampInfoDesc.imageView = stampView;
        stampInfoDesc.sampler = sampler;
        stampInfoDesc.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkDescriptorImageInfo outInfoDesc{};
        outInfoDesc.imageView = outputView;
        outInfoDesc.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

        VkWriteDescriptorSet writes[3] = {};
        writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[0].dstSet = descSet; writes[0].dstBinding = 0; writes[0].descriptorCount = 1; writes[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; writes[0].pImageInfo = &paperInfoDesc;
        writes[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[1].dstSet = descSet; writes[1].dstBinding = 1; writes[1].descriptorCount = 1; writes[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; writes[1].pImageInfo = &stampInfoDesc;
        writes[2].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[2].dstSet = descSet; writes[2].dstBinding = 2; writes[2].descriptorCount = 1; writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE; writes[2].pImageInfo = &outInfoDesc;
        vkUpdateDescriptorSets(engine.getDevice(), 3, writes, 0, nullptr);

        // 7. Transition paper and stamp to SHADER_READ_ONLY_OPTIMAL (they are in GENERAL after upload)
        {
            auto transition = [&](VkImage img) {
                VkCommandBuffer cmd;
                VkCommandBufferAllocateInfo cmdAlloc{};
                cmdAlloc.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
                cmdAlloc.commandPool = engine.getCommandPool();
                cmdAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
                cmdAlloc.commandBufferCount = 1;
                vkAllocateCommandBuffers(engine.getDevice(), &cmdAlloc, &cmd);

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

        // 8. Push constants
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

        // 9. Dispatch
        bool dispatched = engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc),
                                                 (fullWidth + 15) / 16, (fullHeight + 15) / 16, 1);
        if (dispatched) {
            engine.copyImageToHost(outputImage, paperPixels, fullWidth, fullHeight);
        }
    } while (false);

    // ---------- Cleanup (safe even for NULL handles) ----------
    if (sampler)         vkDestroySampler(engine.getDevice(), sampler, nullptr);
    if (descSet)         vkFreeDescriptorSets(engine.getDevice(), engine.getDescriptorPool(), 1, &descSet);
    if (blendLayout)     vkDestroyDescriptorSetLayout(engine.getDevice(), blendLayout, nullptr);
    if (outputView)      engine.destroyImageView(outputView);
    if (outputImage)     engine.destroyImage(outputImage);
    if (stampView)       engine.destroyImageView(stampView);
    if (stampImage)      engine.destroyImage(stampImage);
    if (paperView)       engine.destroyImageView(paperView);
    if (paperImage)      engine.destroyImage(paperImage);

    if (paperPixels)     AndroidBitmap_unlockPixels(env, paperBitmap);
    if (inkPixels)       AndroidBitmap_unlockPixels(env, inkBitmap);
}