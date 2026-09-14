package com.mosaicglobal.finance.shared.messaging;

/** Broker topology owned by the platform. Consumers declare their own queues bound to these. */
public final class FinanceExchanges {

  /** Topic exchange for domain events. Routing key = {@code <module>.<aggregate>.<action>}. */
  public static final String EVENTS = "finance.events";

  /** Direct exchange for explicit work items (send push, export, aggregate...). */
  public static final String COMMANDS = "finance.commands";

  /** Dead-letter exchange; each consumer queue routes its failures to {@code <queue>.parked}. */
  public static final String DLX = "finance.dlx";

  private FinanceExchanges() {}
}
