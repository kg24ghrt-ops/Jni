#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include "shader_loader.h"
#include "vulkan_compute_engine.h"

#define LOG_TAG "InkEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

    VkImage       paperImage  = VK_NULL_HANDLE;
    VkImageView   paperView   = VK_NULL_HANDLE;
    VkImage       stampImage  = VK_NULL_HANDLE;
    VkImageView   stampView   = VK_NULL_HANDLE;
    VkImage       outputImage = VK_NULL_HANDLE;
    VkImageView   outputView  = VK_NULL_HANDLE;
    VkDescriptorSetLayout blendLayout = VK_NULL_HANDLE;
    VkDescriptorSet descSet     = VK_NULL_HANDLE;
    VkSampler     sampler      = VK_NULL_HANDLE;

    AndroidBitmapInfo paperInfo, inkInfo;
    void *paperPixels = nullptr, *inkPixels = nullptr;
    VkDevice device = VK_NULL_HANDLE;

    do {
        if (!engine.initialize()) {
            LOGE("Vulkan engine not available.");
            break;
        }
        device = engine.getDevice();
        LOGI("Vulkan ready.");

        // Lock bitmaps
        if (AndroidBitmap_getInfo(env, paperBitmap, &paperInfo) < 0) break;
        if (AndroidBitmap_getInfo(env, inkBitmap,   &inkInfo)   < 0) break;
        if (AndroidBitmap_lockPixels(env, paperBitmap, &paperPixels) < 0) {
            paperPixels = nullptr;
            break;
        }
        if (AndroidBitmap_lockPixels(env, inkBitmap, &inkPixels) < 0) {
            inkPixels = nullptr;
            break;
        }

        const int fullWidth  = paperInfo.width;
        const int fullHeight = paperInfo.height;
        const int stampW     = inkInfo.width;
        const int stampH     = inkInfo.height;

        // Paper image
        paperImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                        VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        if (!paperImage) break;
        if (!engine.allocateImageMemory(paperImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) break;
        if (!engine.uploadImageData(paperImage, paperPixels, fullWidth, fullHeight)) break;
        paperView = engine.createImageView(paperImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!paperView) break;

        // Stamp image
        stampImage = engine.createImage(stampW, stampH, VK_FORMAT_R8G8B8A8_UNORM,
                                        VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        if (!stampImage) break;
        if (!engine.allocateImageMemory(stampImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) break;
        if (!engine.uploadImageData(stampImage, inkPixels, stampW, stampH)) break;
        stampView = engine.createImageView(stampImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!stampView) break;

        // Output image (device local)
        outputImage = engine.createImage(fullWidth, fullHeight, VK_FORMAT_R8G8B8A8_UNORM,
                                         VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
        if (!outputImage) break;
        if (!engine.allocateImageMemory(outputImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) break;
        outputView = engine.createImageView(outputImage, VK_FORMAT_R8G8B8A8_UNORM);
        if (!outputView) break;

        // Blend shader
        VkShaderModule shaderModule = getInkBlendShaderModule();
        if (!shaderModule) {
            LOGE("Failed to load ink_blend shader.");
            break;
        }

        // Descriptor layout
        VkDescriptorSetLayoutBinding bindings[3] = {};
        bindings[0].binding = 0; bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; bindings[0].descriptorCount = 1; bindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        bindings[1].binding = 1; bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; bindings[1].descriptorCount = 1; bindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        bindings[2].binding = 2; bindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;        bindings[2].descriptorCount = 1; bindings[2].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

        VkDescriptorSetLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        layoutInfo.bindingCount = 3;
        layoutInfo.pBindings = bindings;
        if (vkCreateDescriptorSetLayout(device, &layoutInfo, nullptr, &blendLayout) != VK_SUCCESS) break;

        // Descriptor set
        VkDescriptorSetAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        allocInfo.descriptorPool = engine.getDescriptorPool();
        allocInfo.descriptorSetCount = 1;
        allocInfo.pSetLayouts = &blendLayout;
        if (vkAllocateDescriptorSets(device, &allocInfo, &descSet) != VK_SUCCESS) break;

        // Sampler
        VkSamplerCreateInfo samplerInfo{};
        samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
        samplerInfo.magFilter = VK_FILTER_LINEAR;
        samplerInfo.minFilter = VK_FILTER_LINEAR;
        samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        if (vkCreateSampler(device, &samplerInfo, nullptr, &sampler) != VK_SUCCESS) break;

        // Write descriptor set
        VkDescriptorImageInfo paperDesc{};
        paperDesc.imageView = paperView; paperDesc.sampler = sampler;
        paperDesc.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkDescriptorImageInfo stampDesc{};
        stampDesc.imageView = stampView; stampDesc.sampler = sampler;
        stampDesc.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkDescriptorImageInfo outDesc{};
        outDesc.imageView = outputView; outDesc.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

        VkWriteDescriptorSet writes[3] = {};
        writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[0].dstSet = descSet; writes[0].dstBinding = 0; writes[0].descriptorCount = 1; writes[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; writes[0].pImageInfo = &paperDesc;
        writes[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[1].dstSet = descSet; writes[1].dstBinding = 1; writes[1].descriptorCount = 1; writes[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; writes[1].pImageInfo = &stampDesc;
        writes[2].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; writes[2].dstSet = descSet; writes[2].dstBinding = 2; writes[2].descriptorCount = 1; writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;        writes[2].pImageInfo = &outDesc;
        vkUpdateDescriptorSets(device, 3, writes, 0, nullptr);

        // Transition paper and stamp to SHADER_READ_ONLY_OPTIMAL
        auto transition = [&](VkImage img, VkImageLayout newLayout) -> bool {
            VkCommandBuffer cmd;
            VkCommandBufferAllocateInfo cmdAlloc{};
            cmdAlloc.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
            cmdAlloc.commandPool = engine.getCommandPool();
            cmdAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
            cmdAlloc.commandBufferCount = 1;
            if (vkAllocateCommandBuffers(device, &cmdAlloc, &cmd) != VK_SUCCESS) {
                LOGE("Failed to allocate command buffer for transition.");
                return false;
            }

            VkCommandBufferBeginInfo begin{};
            begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
            begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
            if (vkBeginCommandBuffer(cmd, &begin) != VK_SUCCESS) {
                LOGE("vkBeginCommandBuffer failed during transition.");
                vkFreeCommandBuffers(device, engine.getCommandPool(), 1, &cmd);
                return false;
            }

            VkImageMemoryBarrier barrier{};
            barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.newLayout = newLayout;
            barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.image = img;
            barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
            barrier.dstAccessMask = (newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                                    ? VK_ACCESS_SHADER_READ_BIT
                                    : VK_ACCESS_TRANSFER_READ_BIT;
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                 0, 0, nullptr, 0, nullptr, 1, &barrier);
            vkEndCommandBuffer(cmd);

            VkSubmitInfo submit{};
            submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
            submit.commandBufferCount = 1;
            submit.pCommandBuffers = &cmd;
            vkQueueSubmit(engine.getComputeQueue(), 1, &submit, VK_NULL_HANDLE);
            vkQueueWaitIdle(engine.getComputeQueue());
            vkFreeCommandBuffers(device, engine.getCommandPool(), 1, &cmd);
            return true;
        };

        if (!transition(paperImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)) break;
        if (!transition(stampImage,  VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)) break;

        // Push constants
        struct BlendPush {
            float imageSize[2];
            int32_t stampOffset[2];
            int32_t stampSize[2];
        } pc;
        pc.imageSize[0] = (float)fullWidth;  pc.imageSize[1] = (float)fullHeight;
        pc.stampOffset[0] = offsetX;          pc.stampOffset[1] = offsetY;
        pc.stampSize[0] = stampW;            pc.stampSize[1] = stampH;

        // Dispatch
        if (!engine.dispatchCompute(shaderModule, {descSet}, &pc, sizeof(pc),
                                    (fullWidth + 15) / 16, (fullHeight + 15) / 16, 1)) {
            LOGE("Compute dispatch failed.");
            break;
        }

        // Transition output image for copy (explicit, though engine's copyImageToHost also does it)
        if (!transition(outputImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)) {
            LOGE("Output image transition failed – readback may be corrupted.");
            break;
        }

        // Read back result into bitmap
        engine.copyImageToHost(outputImage, paperPixels, fullWidth, fullHeight);
        LOGI("Ink blended and read back.");

    } while (false);

    // -------------------------------------------------------
    // Cleanup – always destroy sampler and layout, free descSet if possible
    // -------------------------------------------------------
    if (device) {
        if (descSet)     vkFreeDescriptorSets(device, engine.getDescriptorPool(), 1, &descSet);
        if (sampler)     vkDestroySampler(device, sampler, nullptr);
        if (blendLayout) vkDestroyDescriptorSetLayout(device, blendLayout, nullptr);
    }

    if (outputView)  engine.destroyImageView(outputView);
    if (outputImage) engine.destroyImage(outputImage);
    if (stampView)   engine.destroyImageView(stampView);
    if (stampImage)  engine.destroyImage(stampImage);
    if (paperView)   engine.destroyImageView(paperView);
    if (paperImage)  engine.destroyImage(paperImage);

    if (paperPixels) AndroidBitmap_unlockPixels(env, paperBitmap);
    if (inkPixels)   AndroidBitmap_unlockPixels(env, inkBitmap);
}