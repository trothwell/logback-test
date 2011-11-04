package org.trothwell.lbtest;

import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

/**
 * Causes rolling to occur by roles of {@link TimeBasedRollingPolicy} and when
 * instance is closed (which occurs when appender is closed).
 * 
 * @param <E>
 *          not quite sure :/
 */
public class CloseTBRP<E> extends TimeBasedRollingPolicy<E> {
  @Override
  public void stop() {
    try {
      addInfo("Rolling file due to policy closure: " + getActiveFileName());
      synchronized (this) {
        super.rollover();
      }
    } catch (RolloverFailure e) {
      addError("Failed to complete rollover.", e);
    } finally {
      super.stop();
    }
  }
}
