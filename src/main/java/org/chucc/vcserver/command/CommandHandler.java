package org.chucc.vcserver.command;

import org.chucc.vcserver.event.VersionControlEvent;

/**
 * Interface for command handlers in the CQRS pattern.
 * Handlers process commands and produce events, but do not interact with databases.
 *
 * @param <C> the command type
 */
public interface CommandHandler<C extends Command> {
  /**
   * Handles a command and produces an event.
   *
   * @param command the command to handle
   * @return the event produced by handling the command
   * @throws IllegalStateException if the command cannot be handled due to current state
   * @throws IllegalArgumentException if the command is invalid
   */
  VersionControlEvent handle(C command);
}
