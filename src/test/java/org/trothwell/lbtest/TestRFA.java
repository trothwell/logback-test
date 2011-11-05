package org.trothwell.lbtest;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.util.StatusPrinter;

public class TestRFA {
  private static class MyEncoder extends EncoderBase<String> {
    private final String footer;
    private final String header;
    private final byte[] NL;
    private final Charset UTF8;

    MyEncoder(String header, String footer) {
      this.header = header;
      this.footer = footer;
      UTF8 = Charset.forName("UTF-8");
      NL = CoreConstants.LINE_SEPARATOR.getBytes(UTF8);
    }

    public void close() throws IOException {
      if (footer == null) {
        return;
      }
      this.addWarn("Closing: " + footer);
      outputStream.write(footer.getBytes(UTF8));
      outputStream.write(NL);
    }

    public void doEncode(String event) throws IOException {
      synchronized (outputStream) {
        this.addWarn("Encoding: " + event);
        outputStream.write(event.getBytes(UTF8));
        outputStream.write(NL);
      }
    }

    @Override
    public void init(OutputStream os) throws IOException {
      super.init(os);
      if (header == null) {
        return;
      }
      this.addWarn("Opening: " + header);
      outputStream.write(header.getBytes(UTF8));
      outputStream.write(NL);
    }

    @Override
    public String toString() {
      return getClass().getName();
    }
  }

  private Context context;
  private File output;

  @After
  public void cleanupContext() {
    Mockito.verify(context, Mockito.atLeastOnce()).getStatusManager();
    Mockito.verifyNoMoreInteractions(context);

    ContextAwareBase ca = new ContextAwareBase() {
    };
    ca.setContext(context);
    File[] files = output.listFiles();
    // ca.addInfo("Files: " + Arrays.toString(files));
    for (File f : files) {
      if (!f.delete()) {
        ca.addError("Failed to delete file: " + f);
      } else {
        ca.addInfo("Deleted file: " + f);
      }
    }
  }

  @Before
  public void setupContext() {
    StatusManager sm = Mockito.mock(StatusManager.class);
    Mockito.doAnswer(new Answer<Object>() {
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Status s = (Status) invocation.getArguments()[0];
        StringBuilder sb = new StringBuilder();
        StatusPrinter.buildStr(sb, "STATUS: ", s);
        System.out.print(sb);
        return null;
      }
    }).when(sm).add(Mockito.any(Status.class));
    this.context = Mockito.mock(Context.class);
    Mockito.when(context.getStatusManager()).thenReturn(sm);
  }

  @Test
  public void testCloseTBRP() {
    String folderName = "target/log/close_tbrp";
    output = new File(folderName);

    RollingFileAppender<String> rfa = new RollingFileAppender<String>();
    rfa.setContext(context);
    Encoder<String> encoder = new MyEncoder("--INIT--", "--CLOSE--");
    encoder.setContext(context);
    rfa.setEncoder(encoder);
    TimeBasedRollingPolicy<String> rp = new CloseTBRP<String>();
    rp.setContext(context);
    rp.setParent(rfa);
    rp.setFileNamePattern(folderName + "/%d{MM}.%i.txt.zip");
    rp.start();
    SizeAndTimeBasedFNATP<String> tbfnatp = new SizeAndTimeBasedFNATP<String>();
    tbfnatp.setContext(context);
    tbfnatp.setTimeBasedRollingPolicy(rp);
    tbfnatp.setMaxFileSize("10KB");
    tbfnatp.start();
    rp.setTimeBasedFileNamingAndTriggeringPolicy(tbfnatp);
    rfa.setRollingPolicy(rp);
    rfa.start();
    rfa.doAppend("msg");
    rfa.stop();
  }

  @Test
  public void testTBRP() {
    String folderName = "target/log/tbrp";
    output = new File(folderName);

    RollingFileAppender<String> rfa = new RollingFileAppender<String>();
    rfa.setContext(context);
    Encoder<String> encoder = new MyEncoder("--INIT--", "--CLOSE--");
    encoder.setContext(context);
    rfa.setEncoder(encoder);
    TimeBasedRollingPolicy<String> rp = new TimeBasedRollingPolicy<String>();
    rp.setContext(context);
    rp.setParent(rfa);
    rp.setFileNamePattern(folderName + "/%d{MM}.%i.txt.zip");
    rp.start();
    SizeAndTimeBasedFNATP<String> tbfnatp = new SizeAndTimeBasedFNATP<String>();
    tbfnatp.setContext(context);
    tbfnatp.setTimeBasedRollingPolicy(rp);
    tbfnatp.setMaxFileSize("10KB");
    tbfnatp.start();
    rp.setTimeBasedFileNamingAndTriggeringPolicy(tbfnatp);
    rfa.setRollingPolicy(rp);
    rfa.start();
    rfa.doAppend("msg");
    rfa.stop();
  }
}
