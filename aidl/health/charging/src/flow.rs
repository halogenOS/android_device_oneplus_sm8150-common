// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The binder side of the flows of targets attached by the system. The latest target of the
//! attached flow is published to the controller, and `None` once that flow dies.

use binder::{BinderFeatures, DeathRecipient, IBinder, Interface, Status, StatusCode, Strong};
use custom_hardware_health_charging::aidl::custom::hardware::health::charging::{
    ChargeLimitTarget::{ChargeLimitTarget, MAX_LEVEL_PERCENT, MIN_LEVEL_PERCENT},
    IChargeLimitCollector::{BnChargeLimitCollector, IChargeLimitCollector},
    IChargeLimitFlow::IChargeLimitFlow,
};
use log::{error, info, warn};
use tokio::sync::{mpsc, watch};

use crate::{battery::BatteryLevel, error::Error};

/// What the system did. Each event contains the flow it came from.
enum Event {
    /// The system attached a flow of targets.
    Attached(Strong<dyn IChargeLimitFlow>),
    /// A flow emitted a target. `None` means normal charging.
    Emitted { flow: Strong<dyn IChargeLimitFlow>, target: Option<BatteryLevel> },
    /// A flow died with the process that published it.
    FlowDied(Strong<dyn IChargeLimitFlow>),
}

/// Sends the flows attached by the system to [`Flows`] from binder threads.
pub struct Attachments(mpsc::Sender<Event>);

impl Attachments {
    pub fn attach(&self, flow: &Strong<dyn IChargeLimitFlow>) -> binder::Result<()> {
        send(&self.0, Event::Attached(flow.clone()))
    }
}

/// Collects the latest attached flow and publishes its targets.
pub struct Flows {
    /// Handed to the collectors and death recipients of the subscriptions.
    events: mpsc::Sender<Event>,
    inbox: mpsc::Receiver<Event>,
    subscription: Option<Subscription>,
    targets: watch::Sender<Option<BatteryLevel>>,
}

impl Flows {
    /// Returns the flows, the sender of attached flows and the receiver of the targets.
    pub fn new() -> (Self, Attachments, watch::Receiver<Option<BatteryLevel>>) {
        let (events, inbox) = mpsc::channel(1);
        let (targets, receiver) = watch::channel(None);
        let flows = Self { events: events.clone(), inbox, subscription: None, targets };
        (flows, Attachments(events), receiver)
    }

    /// Runs as long as the process. The flows have a sender for their own events, so the channel
    /// never closes.
    pub async fn run(mut self) {
        while let Some(event) = self.inbox.recv().await {
            self.handle(event);
        }
    }

    fn handle(&mut self, event: Event) {
        match event {
            Event::Attached(flow) => {
                self.subscription = Subscription::start(flow, &self.events)
                    .inspect_err(|status| warn!("The attached flow died at once: {status}"))
                    .ok();
                self.publish(None);
            }
            Event::Emitted { flow, target } => {
                if self.subscription.as_ref().is_some_and(|s| s.flow == flow) {
                    self.publish(target);
                }
            }
            Event::FlowDied(flow) => {
                if self.subscription.take_if(|s| s.flow == flow).is_some() {
                    info!("The flow of targets died, charging normally");
                    self.publish(None);
                }
            }
        }
    }

    fn publish(&self, target: Option<BatteryLevel>) {
        self.targets.send_modify(|latest| *latest = target);
    }
}

/// The flow collected by the HAL.
struct Subscription {
    flow: Strong<dyn IChargeLimitFlow>,
    /// Reports the death of the flow until the subscription is dropped.
    _death: DeathRecipient,
}

impl Subscription {
    fn start(
        flow: Strong<dyn IChargeLimitFlow>,
        events: &mpsc::Sender<Event>,
    ) -> Result<Self, Status> {
        let mut death = DeathRecipient::new({
            let (flow, events) = (flow.clone(), events.clone());
            move || {
                if let Err(status) = send(&events, Event::FlowDied(flow.clone())) {
                    error!("Cannot report the death of a flow: {status}");
                }
            }
        });
        flow.as_binder().link_to_death(&mut death)?;
        flow.collect(&Collector::new_binder(flow.clone(), events.clone()))?;
        Ok(Self { flow, _death: death })
    }
}

/// Collects the targets of one flow.
struct Collector {
    flow: Strong<dyn IChargeLimitFlow>,
    events: mpsc::Sender<Event>,
}

impl Collector {
    fn new_binder(
        flow: Strong<dyn IChargeLimitFlow>,
        events: mpsc::Sender<Event>,
    ) -> Strong<dyn IChargeLimitCollector> {
        BnChargeLimitCollector::new_binder(Self { flow, events }, BinderFeatures::default())
    }
}

impl Interface for Collector {}

impl IChargeLimitCollector for Collector {
    fn emit(&self, target: Option<&ChargeLimitTarget>) -> binder::Result<()> {
        let target = target.map(BatteryLevel::try_from).transpose().unwrap_or_else(|error| {
            error!("{}, charging normally", error.describe());
            None
        });
        send(&self.events, Event::Emitted { flow: self.flow.clone(), target })
    }
}

impl TryFrom<&ChargeLimitTarget> for BatteryLevel {
    type Error = Error;

    fn try_from(target: &ChargeLimitTarget) -> Result<Self, Error> {
        Self::within(MIN_LEVEL_PERCENT..=MAX_LEVEL_PERCENT, target.levelPercent)
            .ok_or(Error::TargetOutOfRange(target.levelPercent))
    }
}

/// Sends an event to [`Flows`] from a binder thread. It fails only once the flows have stopped,
/// and the process ends then.
fn send(events: &mpsc::Sender<Event>, event: Event) -> binder::Result<()> {
    events.blocking_send(event).map_err(|_stopped| StatusCode::DEAD_OBJECT.into())
}
