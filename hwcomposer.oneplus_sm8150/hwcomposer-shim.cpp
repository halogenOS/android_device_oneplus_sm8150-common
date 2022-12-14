#define LOG_TAG "hwc-oneplus-sm8150"
#ifndef HWC2_USE_CPP11
#define HWC2_USE_CPP11
#define HWC2_INCLUDE_STRINGIFICATION
#endif

#include <cstdint>
#include <cinttypes>
#include <functional>
#include <string>
#include <string_view>
#include <fstream>
#include <filesystem>
#include <log/log.h>
#include <android-base/properties.h>
#include <hardware/hwcomposer2.h>
#include <dlfcn.h>

#include <core/display_interface.h>
#include "hwcdefs.h"
#include "member_func_ptr.hpp"

using namespace std::string_view_literals;
using namespace sdm;

#define SYSFS_DSI1 "/sys/class/drm/card0-DSI-1/"

#define SYMBOL_SetPreferredColorModeInternal "_ZN3sdm12HWCColorMode29SetPreferredColorModeInternalERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEbPN7android8hardware8graphics6common4V1_19ColorModeEPNS_16DynamicRangeTypeE"
#define SYMBOL_DisplayBuiltinInit            "_ZN3sdm17HWCDisplayBuiltIn4InitEv"
#define SYMBOL_DisplayBuiltinSetPowerMode    "_ZN3sdm17HWCDisplayBuiltIn12SetPowerModeEN4HWC29PowerModeEb"
#define SYMBOL_DisplayBaseSetColorMode       "_ZN3sdm11DisplayBase12SetColorModeERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE"
#define SYMBOL_DisplayBaseSetColorModeById   "_ZN3sdm11DisplayBase16SetColorModeByIdEi"
#define SYMBOL_ColorModePopulateColorModes   "_ZN3sdm12HWCColorMode18PopulateColorModesEv"

// static void *realSetPreferredColorModeInternal;
static void *realDisplayBuiltinInit;
static void *realDisplayBuiltinSetPowerMode;
static void *realDisplayBaseSetColorMode;
static void *realDisplayBaseSetColorModeById;
static void *realColorModePopulateColorModes;

static void *builtin_display_id;
static void *last_initialized_color_mode;
static void *builtin_display_color_mode;
static std::string last_active_mode;

static bool verify_file(const std::filesystem::path &path, std::string_view content) {
    std::ifstream fs(path);
    std::string line;
    std::getline(fs, line);
    return std::string_view(line).find_last_of(content) != std::string_view::npos;
}

static bool write_file(const std::filesystem::path &path, std::string_view content) {
    std::ofstream fs(path);
    fs << content;
    if (fs.fail()) {
        ALOGE("write to %s failed", path.c_str());
        return false;
    }
    return true;
}

static int write_and_verify_file(const std::filesystem::path &path, std::string_view content) {
    if (!write_file(path, content)) return false;
    if (!verify_file(path, content)) {
        ALOGW("verify content of file %s failed", path.c_str());
        return 0;
    }
    return 1;
}

static bool set_panel_mode(std::string_view panel_mode) {
    int result = 1;
    ALOGI("setting panel mode %s", std::string(panel_mode).c_str());
    if (panel_mode == "native_srgb"sv) {
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_srgb_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_loading_effect_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "1"sv);
    } else if (panel_mode == "native_p3"sv) {
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_srgb_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "1"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_loading_effect_mode", "1"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "dither_en", "1"sv);
    } else if (panel_mode == "hal_hdr"sv) {
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_srgb_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "1"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_loading_effect_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "dither_en", "1"sv);
    } else if (panel_mode == "advance_p3"sv) {
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_srgb_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_p3_mode", "1"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_loading_effect_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "dither_en", "1"sv);
    } else if (panel_mode == "advance_srgb"sv) {
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_srgb_mode", "1"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_loading_effect_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "dither_en", "0"sv);
    } else if (panel_mode == "panel_native"sv) {
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_srgb_color_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_srgb_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_customer_p3_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_wide_color_mode", "1"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "native_display_loading_effect_mode", "0"sv);
        result &= write_and_verify_file(SYSFS_DSI1 "dither_en", "0"sv);
    } else {
        ALOGE("unrecognized panel mode %s", std::string(panel_mode).c_str());
        result = 0;
    }
    if (result) {
        last_active_mode = panel_mode;
    }
    return result;
}

static bool load_calibration(std::string_view sdm_mode) {
    ALOGV("load_calibration for sdm color mode %s", std::string(sdm_mode).c_str());
    if (sdm_mode == "hal_srgb"sv) {
        auto panel_mode = android::base::GetProperty("persist.vendor.display.hal_srgb_mode", "native_srgb");
        return set_panel_mode(panel_mode);
    } else if (sdm_mode == "hal_display_p3"sv) {
        auto panel_mode = android::base::GetProperty("persist.vendor.display.hal_display_p3_mode", "native_p3");
        return set_panel_mode(panel_mode);
    } else if (sdm_mode == "hal_saturated"sv) {
        auto panel_mode = android::base::GetProperty("persist.vendor.display.hal_saturated_mode", "native_p3");
        return set_panel_mode(panel_mode);
    } else if (sdm_mode == "hal_hdr"sv) {
        return set_panel_mode("hal_hdr"sv);
    } else {
        ALOGE("unrecognized sdm mode %s", std::string(sdm_mode).c_str());
        return false;
    }
};

struct HookHWCDisplayBuiltin {
    int DisplayBuiltinInit() __asm__(SYMBOL_DisplayBuiltinInit);
    HWC2::Error DisplayBuiltinSetPowerMode(HWC2::PowerMode mode, bool teardown) __asm__(SYMBOL_DisplayBuiltinSetPowerMode);

    void *vtbl;
    bool validated;
    bool layer_stack_invalid_;
    /*CoreInterface*/ void *core_intf_;
    /*HWCBufferAllocator*/ void *buffer_allocator_;
    /*HWCCallbacks*/ void *callbacks_;
    /*HWCDisplayEventHandler*/ void *event_handler_;
    /*DisplayType*/ int type_;
    hwc2_display_t hal_id_;
    int32_t sdm_id_;
    bool needs_blit_;
    /*DisplayInterface*/ void *display_intf_;
};

struct HookHWCColorMode {
    HWC2::Error PopulateColorModes() __asm__(SYMBOL_ColorModePopulateColorModes);
    DisplayInterface *display_intf_;
    bool apply_mode_;
    ColorMode current_color_mode_;
    RenderIntent current_render_intent_;
    DynamicRangeType curr_dynamic_range_;
    typedef std::map<DynamicRangeType, std::string> DynamicRangeMap;
    typedef std::map<RenderIntent, DynamicRangeMap> RenderIntentMap;
    std::map<ColorMode, RenderIntentMap> color_mode_map_;
    double color_matrix_[16];
    std::map<ColorMode, DynamicRangeMap> preferred_mode_;
};

struct HookDisplayBase /* : public DisplayInterface */ {
    DisplayError SetColorMode(const std::string &color_mode) __asm__(SYMBOL_DisplayBaseSetColorMode);
    DisplayError SetColorModeById(int32_t color_mode_id) __asm__(SYMBOL_DisplayBaseSetColorModeById);
};

void update_mode_preference(HookHWCColorMode *color_mode_) {
    // WIP: looking for a victim function to make changed value applied immediately.
    // once implemented, we can switch back to stock qdcm_calib_data and make use of extra color modes.
    auto srgb_mode = android::base::GetProperty("persist.vendor.display.hal_srgb_mode", "native_srgb");
    auto display_p3_mode = android::base::GetProperty("persist.vendor.display.hal_display_p3_mode", "native_p3");
    auto native_mode = android::base::GetProperty("persist.vendor.display.hal_native_mode", "native_p3");
    color_mode_->color_mode_map_[ColorMode::SRGB][RenderIntent::COLORIMETRIC][kSdrType] = srgb_mode;
    color_mode_->color_mode_map_[ColorMode::SRGB][RenderIntent::ENHANCE][kSdrType] = srgb_mode;
    color_mode_->preferred_mode_[ColorMode::SRGB][kSdrType] = srgb_mode;
    color_mode_->color_mode_map_[ColorMode::DISPLAY_P3][RenderIntent::COLORIMETRIC][kSdrType] = display_p3_mode;
    color_mode_->color_mode_map_[ColorMode::DISPLAY_P3][RenderIntent::ENHANCE][kSdrType] = display_p3_mode;
    color_mode_->preferred_mode_[ColorMode::DISPLAY_P3][kSdrType] = display_p3_mode;
    color_mode_->color_mode_map_[ColorMode::NATIVE][RenderIntent::COLORIMETRIC][kSdrType] = native_mode;
    color_mode_->preferred_mode_[ColorMode::NATIVE][kSdrType] = native_mode;
}

DisplayError HookDisplayBase::SetColorMode(const std::string &color_mode) {
    auto origin_func = member_func_ptr_cast<decltype(&HookDisplayBase::SetColorMode)>(realDisplayBaseSetColorMode);
    // auto display_intf = reinterpret_cast<DisplayInterface*>(this);
    auto err = std::invoke(origin_func, this, color_mode);
    ALOGV("set sdm color mode %s for display %p", color_mode.c_str(), this);
    if (reinterpret_cast<void*>(this) != builtin_display_id || err) return err;
    auto result = load_calibration(color_mode);
    return result ? kErrorNone : kErrorParameters;
}

DisplayError HookDisplayBase::SetColorModeById(int32_t color_mode_id) {
    auto origin_func = member_func_ptr_cast<decltype(&HookDisplayBase::SetColorModeById)>(realDisplayBaseSetColorModeById);
    auto err = std::invoke(origin_func, this, color_mode_id);
    ALOGV("set sdm color mode %d for display %p", color_mode_id, this);
    if (reinterpret_cast<void*>(this) != builtin_display_id || err) return err;
    auto display_intf = reinterpret_cast<DisplayInterface*>(this);
    std::string name;
    err = display_intf->GetColorModeName(color_mode_id, &name);
    if (err != kErrorNone) return err;
    auto result = load_calibration(name);
    return result ? kErrorNone : kErrorParameters;
}

int HookHWCDisplayBuiltin::DisplayBuiltinInit() {
    auto origin_func = member_func_ptr_cast<decltype(&HookHWCDisplayBuiltin::DisplayBuiltinInit)>(realDisplayBuiltinInit);
    auto err = std::invoke(origin_func, this);
    if (!err && !builtin_display_id) {
        builtin_display_id = display_intf_;
        builtin_display_color_mode = last_initialized_color_mode;
        ALOGI("HWCDisplayBuiltin::Init on display_intf %p color_mode %p", display_intf_, builtin_display_color_mode);
    }
    return err;
}

HWC2::Error HookHWCDisplayBuiltin::DisplayBuiltinSetPowerMode(HWC2::PowerMode mode, bool teardown) {
    static bool first_call = true;
    auto origin_func = member_func_ptr_cast<decltype(&HookHWCDisplayBuiltin::DisplayBuiltinSetPowerMode)>(realDisplayBuiltinSetPowerMode);
    auto err = std::invoke(origin_func, this, mode, teardown);
    if (err == HWC2::Error::None && !first_call && display_intf_ == builtin_display_id && mode == HWC2::PowerMode::On) {
        ALOGV("reloading panel mode %s", last_active_mode.c_str());
        set_panel_mode(last_active_mode);
        first_call = false;
    }
    return err;
}

HWC2::Error HookHWCColorMode::PopulateColorModes() {
    ALOGV("PopulateColorModes on HWCColor %p for display %p", this, display_intf_);
    last_initialized_color_mode = reinterpret_cast<void*>(this);;
    auto origin_func = member_func_ptr_cast<decltype(&HookHWCColorMode::PopulateColorModes)>(realColorModePopulateColorModes);
    return std::invoke(origin_func, this);
}

__attribute__((constructor)) void __hwc_shim_init()
{

    #define FILL_SHIM(name) do { \
        real ## name = dlsym(RTLD_NEXT, SYMBOL_ ## name); \
        if (!real ## name) LOG_ALWAYS_FATAL("cannot get original %s", SYMBOL_ ## name); \
    } while(0)

    // FILL_SHIM(SetPreferredColorModeInternal);
    FILL_SHIM(DisplayBaseSetColorMode);
    FILL_SHIM(DisplayBaseSetColorModeById);
    FILL_SHIM(DisplayBuiltinInit);
    FILL_SHIM(DisplayBuiltinSetPowerMode);
    FILL_SHIM(ColorModePopulateColorModes);

    #undef FILL_SHIM
}

// a symbol reference is needed to fool the linker to add a DT_NEEDED to hwcomposer.msmnile.so
// the symbol needs to exist in both real and fake hwcomposer.msmnile.so
void fool_linker() {
    void fool_linker_external() __asm__("_ZN3sdm8HWCLayerD1Ev");
    fool_linker_external();
}
