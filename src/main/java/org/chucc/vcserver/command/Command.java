package org.chucc.vcserver.command;

/**
 * Marker interface for all CQRS commands.
 * Commands represent intent to change the system state and produce events.
 */
public interface Command {
  /**
   * Gets the dataset name this command applies to.
   *
   * @return the dataset name
   */
  String dataset();
}
