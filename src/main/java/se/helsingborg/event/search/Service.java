package se.helsingborg.event.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * @author kalle
 * @since 2015-10-24 11:27
 */
public class Service {

  private static final Logger log = LoggerFactory.getLogger(Service.class);

  private static Service instance = new Service();

  public static Service getInstance() {
    return instance;
  }

  private Service() {

  }

  private Properties properties;
  private File dataPath;

  private IndexManager indexManager;

  public void open() throws Exception {

    log.info("Starting up...");

    properties = new Properties();
    properties.load(getClass().getResourceAsStream("/settings.properties"));

    dataPath = new File(properties.getProperty("DataPath", "./data"));
    if (!dataPath.exists() && !dataPath.mkdirs()) {
      throw new IOException("Could not mkdirs " + dataPath.getAbsolutePath());
    }

    indexManager.open();

    log.info("Service has been started.");
  }

  public void close() throws Exception {

    log.info("Service closing...");

    indexManager.close();

    log.info("Service has been closed.");

  }

  public Properties getProperties() {
    return properties;
  }

  public void setProperties(Properties properties) {
    this.properties = properties;
  }

  public File getDataPath() {
    return dataPath;
  }

  public void setDataPath(File dataPath) {
    this.dataPath = dataPath;
  }

}