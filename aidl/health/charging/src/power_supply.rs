// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Power supply uevents of the kernel: a charger plugged in or out, a new battery level.

use kobject_uevent::UEvent;
use netlink_sys::{
    protocols::NETLINK_KOBJECT_UEVENT, AsyncSocket, AsyncSocketExt, SocketAddr, TokioSocket,
};

use crate::error::Error;

/// The netlink multicast group the kernel sends uevents to, from `kobject_uevent_net_broadcast` in
/// `lib/kobject_uevent.c` of the kernel.
const KERNEL_UEVENT_GROUP: u32 = 1;

/// The subsystem the kernel sets on power supply uevents.
const POWER_SUPPLY_SUBSYSTEM: &str = "power_supply";

/// The uevents of the power supplies.
pub struct PowerSupplyEvents(TokioSocket);

impl PowerSupplyEvents {
    /// Subscribes to the uevents of the kernel. Runs inside the Tokio runtime.
    pub fn open() -> Result<Self, Error> {
        let mut socket = TokioSocket::new(NETLINK_KOBJECT_UEVENT).map_err(Error::OpenUevents)?;
        // Port 0 makes the kernel set the port of the socket.
        socket
            .socket_mut()
            .bind(&SocketAddr::new(0, KERNEL_UEVENT_GROUP))
            .map_err(Error::OpenUevents)?;
        Ok(Self(socket))
    }

    /// Waits until a power supply changed.
    pub async fn changed(&self) -> Result<(), Error> {
        loop {
            match self.0.recv_from_full().await {
                Ok((packet, _kernel)) => {
                    if is_power_supply(&packet) {
                        return Ok(());
                    }
                }
                // The kernel dropped uevents while the socket was full, and some of them may have
                // come from a power supply.
                Err(error) if error.raw_os_error() == Some(libc::ENOBUFS) => return Ok(()),
                Err(error) => return Err(Error::ReceiveUevents(error)),
            }
        }
    }
}

/// A packet that does not parse as a uevent does not come from a power supply.
fn is_power_supply(packet: &[u8]) -> bool {
    UEvent::from_netlink_packet(packet).is_ok_and(|event| event.subsystem == POWER_SUPPLY_SUBSYSTEM)
}
