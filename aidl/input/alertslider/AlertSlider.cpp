/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2026 The halogenOS Project
 */

#include "AlertSlider.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>

#include <dirent.h>
#include <fcntl.h>
#include <linux/input.h>
#include <unistd.h>

#include <string>

using ::android::base::ReadFileToString;
using ::android::base::Trim;

namespace aidl::vendor::oplus::hardware::alertslider {

static const char* const kDeviceNames[] = {
    "oplus,hall_tri_state_key",
    "oplus,tri-state-key",
};

AlertSlider::AlertSlider() : mPosition(readPosition()) {
    mThread = std::thread(&AlertSlider::watchThread, this);
}

AlertSlider::~AlertSlider() {
    {
        std::lock_guard<std::mutex> lock(mLock);
        mRunning = false;
    }
    mCv.notify_all();
    if (mThread.joinable()) mThread.join();
}

AlertSliderPosition AlertSlider::readPosition() {
    std::string value;
    if (!ReadFileToString(kTriStatePath, &value)) {
        LOG(ERROR) << "Failed to read tri-state key";
        return AlertSliderPosition::TOP;
    }
    switch (std::stoi(Trim(value))) {
        case 1: return AlertSliderPosition::TOP;
        case 2: return AlertSliderPosition::MIDDLE;
        case 3: return AlertSliderPosition::BOTTOM;
        default:
            LOG(ERROR) << "Unknown position: " << value;
            return AlertSliderPosition::TOP;
    }
}

int AlertSlider::findInputDevice() {
    DIR* dir = opendir(kSysClassInput);
    if (!dir) return -1;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;

        std::string namePath = std::string(kSysClassInput) + "/" + entry->d_name + "/device/name";
        std::string name;
        if (!ReadFileToString(namePath, &name)) continue;
        name = Trim(name);

        for (const auto* devName : kDeviceNames) {
            if (name == devName) {
                std::string devPath = std::string(kInputDir) + "/" + entry->d_name;
                int fd = open(devPath.c_str(), O_RDONLY);
                closedir(dir);
                return fd;
            }
        }
    }
    closedir(dir);
    return -1;
}

void AlertSlider::watchThread() {
    int fd = findInputDevice();
    if (fd < 0) {
        LOG(ERROR) << "Tri-state key input device not found";
        return;
    }

    LOG(INFO) << "Watching tri-state key input device";

    struct input_event ev;
    while (mRunning) {
        ssize_t n = read(fd, &ev, sizeof(ev));
        if (n != sizeof(ev)) break;

        // Only react to key events
        if (ev.type != EV_KEY) continue;

        auto pos = readPosition();
        {
            std::lock_guard<std::mutex> lock(mLock);
            mPosition = pos;
        }
        mCv.notify_all();
    }

    close(fd);
}

ndk::ScopedAStatus AlertSlider::getPosition(AlertSliderPosition* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    *_aidl_return = mPosition;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AlertSlider::waitForChange(AlertSliderPosition* _aidl_return) {
    std::unique_lock<std::mutex> lock(mLock);
    auto current = mPosition;
    mCv.wait(lock, [&] { return mPosition != current || !mRunning; });
    *_aidl_return = mPosition;
    return ndk::ScopedAStatus::ok();
}

}  // namespace aidl::vendor::oplus::hardware::alertslider
