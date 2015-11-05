package se.helsingborg.event.sources.cbis;

import junit.framework.TestCase;
import se.helsingborg.event.domin.Event;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kalle
 * @since 2015-11-05 05:01
 */
public class TestCBISExportReader extends TestCase {

  public void test() throws Exception {

    CBISExportReader reader = new CBISExportReader(new InputStreamReader(getClass().getResourceAsStream("/CBIS-export/evenemang.csv"), "UTF8"));
    try {

      List<Event> events = new ArrayList<>();
      Event event;

      while ((event = reader.readEvent()) != null) {
        events.add(event);
      }

      System.currentTimeMillis();

    } finally {
      reader.close();
    }

  }

}
