package org.trothwell.lbtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

public class TestCloseTBRP {
  /**
   * Using a counter makes it a bit easier to determine which temp folder is
   * which when looking at logs.
   */
  private static int counter = 0;
  private LoggerContext lc;
  private File tmp;

  @After
  public void clean() throws IOException {
    MDC.clear();

    try {
      lc.stop();
    } catch (RuntimeException e) {
      echo("Exception while stopping: " + e.getClass().getSimpleName() + " - "
          + e.getMessage());
      throw e;
    } finally {
      // Always try to delete temp files
      recursiveDelete(tmp);
    }
  }

  @Before
  public void setup() throws IOException {
    lc = new LoggerContext();
    tmp = new File("target/logs/" + TestCloseTBRP.class.getSimpleName() + "_"
        + (counter++));
  }

  @Test
  public void testCloseTBRP() throws IOException, JoranException {
    URL cfg = TestCloseTBRP.class.getResource("config_closetbrp.xml");
    assertNotNull(cfg);

    lc.putProperty("TEMP_FOLDER", tmp.getAbsolutePath());
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(lc);
    configurator.doConfigure(cfg);

    Logger log = lc.getLogger("Test");
    log.info("Test1"); // log to unknown
    MDC.put("KEY", "key");
    log.info("Test2"); // log to specific

    lc.stop();

    try {
      // Wait for async delete to complete
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException e) {
    }

    List<String> txts = recursiveList(tmp, new FileFilter() {
      public boolean accept(File f) {
        return f.getName().endsWith(".txt");
      }
    });
    // FIXME: have FileAppender release lock
    assertEquals("The raw shouldn't exist.", 0, txts.size());

    List<String> zips = recursiveList(tmp, new FileFilter() {
      public boolean accept(File f) {
        return f.getName().endsWith(".zip");
      }
    });
    assertEquals("The raw should be converted to zip", 2, zips.size());

  }

  @Test
  public void testTBRP() throws IOException, JoranException {
    URL cfg = TestCloseTBRP.class.getResource("config_tbrp.xml");
    assertNotNull(cfg);

    LoggerContext lc = new LoggerContext();
    lc.putProperty("TEMP_FOLDER", tmp.getAbsolutePath());
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(lc);
    configurator.doConfigure(cfg);

    Logger log = lc.getLogger("Test");
    log.info("Test1"); // log to unknown
    MDC.put("KEY", "key");
    log.info("Test2"); // log to specific

    lc.stop();

    try {
      // TODO: Any async processes to wait for?
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException e) {
    }

    List<String> txts = recursiveList(tmp, new FileFilter() {
      public boolean accept(File f) {
        return f.getName().endsWith(".txt");
      }
    });
    assertEquals("The 2 raw text files.", 2, txts.size());

    List<String> zips = recursiveList(tmp, new FileFilter() {
      public boolean accept(File f) {
        return f.getName().endsWith(".zip");
      }
    });
    assertEquals("No zips should have been converted", 0, zips.size());
  }

  private void echo(String msg) {
    System.out.println(msg);
  }

  private void recursiveDelete(File folder) throws IOException {
    for (File child : folder.listFiles()) {
      if (child.isDirectory()) {
        recursiveDelete(child);
      } else if (child.isFile()) {
        echo("Deleting file: " + child.getPath());
        if (!child.delete()) {
          throw new IOException("Failed to delete file: "
              + child.getAbsolutePath());
        }
      } else if (child.exists()) {
        // ?
      } else {
      }
    }
    echo("Deleting folder: " + folder.getPath());
    if (!folder.delete()) {
      throw new IOException("Failed to delete folder: "
          + folder.getAbsolutePath());
    }
  }

  private List<String> recursiveList(File folder, FileFilter filter) {
    List<String> retval = new ArrayList<String>();
    for (File child : folder.listFiles()) {
      if (filter.accept(child)) {
        retval.add(child.getAbsolutePath());
      }
      if (child.isDirectory()) {
        retval.addAll(recursiveList(child, filter));
      } else {
        // file
      }
    }
    return retval;
  }
}
