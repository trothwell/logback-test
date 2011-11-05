package org.trothwell.lbtest;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.helper.AsynchronousCompressor;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.rolling.helper.Compressor;
import ch.qos.logback.core.rolling.helper.FileFilterUtil;
import ch.qos.logback.core.rolling.helper.RenameUtil;

/**
 * Causes rolling to occur by roles of {@link TimeBasedRollingPolicy} and when
 * instance is closed (which occurs when appender is closed).
 * 
 * @param <E>
 *          Information type.
 */
public class CloseTBRP<E> extends TimeBasedRollingPolicy<E> {
  private boolean hasRollOccurred;
  private final RenameUtil renameUtil;

  public CloseTBRP() {
    super();
    this.renameUtil = new RenameUtil();
    this.hasRollOccurred = false;
  }

  @Override
  public void stop() {
    try {
      if (!hasRollOccurred) {
        hasRollOccurred = true;
        addInfo("Rolling file due to policy closure: " + getActiveFileName());
        mySyncRollover();
      } else {
        // FIXME: Is it intended that this rolling policy should be stopped
        // twice?
        addWarn("Double entry into stop.", new Throwable());
      }
    } catch (RolloverFailure e) {
      addError("Failed to complete rollover.", e);
    } finally {
      super.stop();
    }
  }

  @Override
  public String toString() {
    return "o.trothwell.lbtest.CloseTBRP";
  }

  private Future<?> asyncDelete(final Future<?> fileTask, final File f) {
    Callable<?> task = new Callable<Object>() {
      public Object call() throws Exception {
        while (!fileTask.isDone()) {
          System.out.println("WAITING FOR FILE TASK TO COMPLETE: "
              + f.getPath());
          TimeUnit.MILLISECONDS.sleep(10);
        }
        if (!f.isFile()) {
          System.out.println("FILE TASK DELETE FILE: " + f.getPath());
          return null;
        }

        while (f.isFile()) {
          if (f.delete()) {
            System.out.println("DELETED: " + f.getPath());
            return null;
          }
          System.out.println("WAITING TO DELETE: " + f.getPath());
          TimeUnit.MILLISECONDS.sleep(50);
        }
        System.out.println("FILE DELETED BY UNKNOWN: " + f.getPath());

        return null;
      }
    };
    return Executors.newSingleThreadExecutor().submit(task);
  }

  private Future<?> myAsyncCompress(String nameOfFile2Compress,
      String nameOfCompressedFile, String innerEntryName)
      throws RolloverFailure {
    Compressor compressor = new Compressor(compressionMode);
    compressor.setContext(context);
    AsynchronousCompressor ac = new AsynchronousCompressor(compressor);
    // FIXME: since appender has lock on file, the source file will not be
    // deleted
    Future<?> retval = ac.compressAsynchronously(nameOfFile2Compress,
        nameOfCompressedFile, innerEntryName);
    asyncDelete(retval, new File(nameOfFile2Compress));
    return retval;
  }

  private synchronized void mySyncRollover() throws RolloverFailure {
    final String src = getParentsRawFileProperty();
    if (compressionMode == CompressionMode.NONE) {
      if (src == null) {
        // nothing more to do
      } else {
        // FIXME: Another way to get filename (uses method intended only for
        // testing)
        TimeBasedFileNamingAndTriggeringPolicy<E> trigger = getTimeBasedFileNamingAndTriggeringPolicy();
        trigger.setCurrentTime(System.currentTimeMillis());
        String elapsedPeriodsFileName = trigger
            .getCurrentPeriodsFileNameWithoutCompressionSuffix();

        RenameUtil renameUtil = new RenameUtil();
        // FIXME: since appender has lock on file, this will fail
        renameUtil.rename(src, elapsedPeriodsFileName);
      }
    } else {
      // FIXME: Another way to get filename (uses method intended only for
      // testing)
      TimeBasedFileNamingAndTriggeringPolicy<E> trigger = getTimeBasedFileNamingAndTriggeringPolicy();
      trigger.setCurrentTime(System.currentTimeMillis());
      String elapsedPeriodsFileName = trigger
          .getCurrentPeriodsFileNameWithoutCompressionSuffix();
      String elapsedPeriodStem = FileFilterUtil
          .afterLastSlash(elapsedPeriodsFileName);

      final Future<?> task;
      if (src == null) {
        task = myAsyncCompress(elapsedPeriodsFileName, elapsedPeriodsFileName,
            elapsedPeriodStem);
      } else {
        // FIXME: since appender has lock on file, this will fail
        String tmpTarget = src + System.nanoTime() + ".tmp";
        renameUtil.rename(src, tmpTarget);
        task = myAsyncCompress(tmpTarget, elapsedPeriodsFileName,
            elapsedPeriodStem);
      }
      while (!task.isDone()) {
        System.out.println("Waiting for file to be compressed.");
        try {
          TimeUnit.MILLISECONDS.sleep(250);
        } catch (InterruptedException e) {
        }
      }
      System.out.println("Compresion completed.");
    }
  }
}
