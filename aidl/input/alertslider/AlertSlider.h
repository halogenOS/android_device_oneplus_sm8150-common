/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2026 The halogenOS Project
 */

#pragma once

#include <aidl/vendor/oplus/hardware/alertslider/BnAlertSlider.h>

#include <condition_variable>
#include <mutex>
#include <thread>

namespace aidl::vendor::oplus::hardware::alertslider {

class AlertSlider : public BnAlertSlider {
  public:
    AlertSlider();
    ~AlertSlider();

    ndk::ScopedAStatus getPosition(AlertSliderPosition* _aidl_return) override;
    ndk::ScopedAStatus waitForChange(AlertSliderPosition* _aidl_return) override;

  private:
    static constexpr const char* kTriStatePath = "/proc/tristatekey/tri_state";
    static constexpr const char* kInputDir = "/dev/input";
    static constexpr const char* kSysClassInput = "/sys/class/input";

    AlertSliderPosition readPosition();
    int findInputDevice();
    void watchThread();

    std::thread mThread;
    std::mutex mLock;
    std::condition_variable mCv;
    AlertSliderPosition mPosition;
    bool mRunning = true;
};

}  // namespace aidl::vendor::oplus::hardware::alertslider
