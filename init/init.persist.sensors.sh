#!/vendor/bin/sh

# https://github.com/niravAjnalnes/vendor_qcom_proprietary/blob/36fc163a534963a5b3af52186af5efcc63401ad2/sensors-see/sensors-hal-2.0/framework/sensor_factory.cpp#L49C51-L49C95
# The sensors list file needs to be updated otherwise it will delay boot for 30 seconds
sensors_list_file="/mnt/vendor/persist/sensors/sensors_list.txt"
# Assume the file is out of date when "printf_ap" is found.
# TODO: use better compare
if [ -f "$sensors_list_file" ] && grep -q "printf_ap" "$sensors_list_file"; then
    # This list was obtained by extracting the sensors_list.txt file from a phone
    # with a fresh install and removing the offending entries that are not found
    # during boot
    # See:
    # E sensors-hal: wait_for_mandatory_sensors:529, some mandatory sensors not available even after 30 seconds, giving up.
    # E sensors-hal: wait_for_mandatory_sensors:531, 7 missing sensor(s)
    # E sensors-hal: wait_for_mandatory_sensors:534,     als_motion
    # E sensors-hal: wait_for_mandatory_sensors:534,     ambient_light
    # E sensors-hal: wait_for_mandatory_sensors:534,     op_free_fall
    # E sensors-hal: wait_for_mandatory_sensors:534,     op_motion_detect
    # E sensors-hal: wait_for_mandatory_sensors:534,     pickup
    # E sensors-hal: wait_for_mandatory_sensors:534,     pocket
    # E sensors-hal: wait_for_mandatory_sensors:534,     printf_ap
    # Another list can be found here: https://github.com/niravAjnalnes/vendor_qcom_proprietary/blob/36fc163a534963a5b3af52186af5efcc63401ad2/sensors-see/sensors-hal-2.0/sensors_list.txt
    cat > "$sensors_list_file" <<EOT
wise_light
sars
pedometer_minute
oplus_activity_recognition
gravity
sig_motion
pick_up_motion
lux_aod
sensor_logger
proximity
sensor_temperature
device_orient
psmd
amd_oplus
game_rv
geomag_rv
pedometer
camera_protect
mag
gyro
accel
resampler
tilt
gyro_cal
mag_cal
rotv
EOT
fi
