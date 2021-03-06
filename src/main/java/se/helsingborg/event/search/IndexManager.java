package se.helsingborg.event.search;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.helsingborg.event.domin.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author kalle
 * @since 2015-10-24 11:44
 */
public class IndexManager {

  private static final Logger log = LoggerFactory.getLogger(IndexManager.class);


  public static final String FIELD_EVENT_IDENTITY_INDEXED = "Event#identity";
  public static final String FIELD_EVENT_IDENTITY_VALUE = "Event#identity[value]";
  public static final String FIELD_EVENT_JSON_VALUE = "Event#json[value]";

  public static final String FIELD_EVENT_CREATED = "Event#created";
  public static final String FIELD_EVENT_MODIFIED = "Event#modified";


  // todo: future, index bboxes rather than coordinates in order to query for polygons.
//  public static final String FIELD_EVENT_LOCATION_GEO_SOUTH_LATITUDE = "Event.location.geo#south latitude";
//  public static final String FIELD_EVENT_LOCATION_GEO_WEST_LONGITUDE = "Event.location.geo#west longitude";
//  public static final String FIELD_EVENT_LOCATION_GEO_NORTH_LATITUDE = "Event.location.geo#north latitude";
//  public static final String FIELD_EVENT_LOCATION_GEO_EAST_LONGITUDE = "Event.location.geo#east longitude";

  public static final String FIELD_EVENT_LOCATION_GEO_LATITUDE = "Event.location.geo#latitude";
  public static final String FIELD_EVENT_LOCATION_GEO_LONGITUDE = "Event.location.geo#longitude";


  public static final String FIELD_EVENT_COMBINED_TEXT_NGRAMS = "Event#combined text ngrams";

  public static final String FIELD_EVENT_TAG = "Event#tag";
  public static final String FIELD_EVENT_NAME = "Event#name";
  public static final String FIELD_EVENT_DESCRIPTION = "Event#description";

  public static final String FIELD_EVENT_LOCATION_NAME = "Event.location#name";

  public static final String FIELD_EVENT_LOCATION_POSTAL_ADDRESS_NAME = "Event.location.postalAddress#name";
  public static final String FIELD_EVENT_LOCATION_POSTAL_ADDRESS_STREET_ADDRESS = "Event.location.postalAddress#street address";
  public static final String FIELD_EVENT_LOCATION_POSTAL_ADDRESS_POSTAL_CODE = "Event.location.postalAddress#postal code";
  public static final String FIELD_EVENT_LOCATION_POSTAL_ADDRESS_POSTAL_TOWN = "Event.location.postalAddress#postal town";
  public static final String FIELD_EVENT_LOCATION_POSTAL_ADDRESS_COUNTRY = "Event.location.postalAddress#postal country";

  public static final String FIELD_EVENT_SHOW_STATUS = "Event.show#status";

  public static final String FIELD_EVENT_SHOW_START_DATE_TIME = "Event.show#start date time";
  public static final String FIELD_EVENT_SHOW__END_DATE_TIME = "Event.show#end date time";

  private File dataPath;

  private Directory directory;
  private IndexWriter indexWriter;
  private SearcherManager searcherManager;

  public void open() throws Exception {

    log.info("Starting up...");

    if (dataPath == null) {
      dataPath = new File(Service.getInstance().getDataPath(), "lucene");
      log.warn("Event index data path not set, defaulting to " + dataPath.getAbsolutePath());
    }

    if (!dataPath.exists() && !dataPath.mkdirs()) {
      log.error("Could not mkdirs " + dataPath.getAbsolutePath());
    }

    directory = FSDirectory.open(dataPath.toPath());

    IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new EventIndexAnalyzerBuilder().build());
    indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

    indexWriter = new IndexWriter(directory, indexWriterConfig);
    searcherManager = new SearcherManager(indexWriter, true, new SearcherFactory());

    // todo: if index is empty then reconstruct

    log.info("Started.");
  }

  public void close() throws Exception {
    log.info("Closing...");

    searcherManager.close();
    indexWriter.close();
    directory.close();

    log.info("Closed.");
  }


  public void commit() throws Exception {
    indexWriter.commit();
    searcherManager.maybeRefresh();
  }

  public void updateIndex(Event event, JSONObject json) throws Exception {

    List<Document> documents = new ArrayList<>();

    String eventIdString = String.valueOf(event.getEventId());

    if (event.getShows() != null && !event.getShows().isEmpty()) {
      for (Show show : event.getShows()) {
        documents.add(documentFactory(event, json, show));
      }
    } else {
      documents.add(documentFactory(event, json));
    }

    Term identityTerm = new Term(FIELD_EVENT_IDENTITY_INDEXED, eventIdString);
    indexWriter.updateDocuments(identityTerm, documents);


  }


  private Document documentFactory(Event event, JSONObject json) throws Exception {

    String eventIdString = String.valueOf(event.getEventId());

    final Document document = new Document();

    document.add(new BinaryDocValuesField(FIELD_EVENT_JSON_VALUE, new BytesRef(json.toString())));

    document.add(new NumericDocValuesField(FIELD_EVENT_IDENTITY_VALUE, event.getEventId()));
    document.add(new StringField(FIELD_EVENT_IDENTITY_INDEXED, eventIdString, Field.Store.NO));

    document.add(new LongField(FIELD_EVENT_CREATED, event.getCreatedEpochMilliseconds(), StoredField.Store.NO));
    document.add(new LongField(FIELD_EVENT_MODIFIED, event.getModifiedEpochMilliseconds(), StoredField.Store.NO));


    if (event.getName() != null) {
      document.add(new TextField(FIELD_EVENT_NAME, event.getName(), Field.Store.NO));
    }
    if (event.getDescription() != null) {
      document.add(new TextField(FIELD_EVENT_DESCRIPTION, event.getDescription(), Field.Store.NO));
    }

    if (event.getTags() != null && !event.getTags().isEmpty()) {
      for (String tag : event.getTags()) {
        document.add(new TextField(FIELD_EVENT_TAG, tag, Field.Store.NO));
      }


    }

    if (event.getLocation() != null) {

      if (event.getLocation().getName() != null) {
        document.add(new TextField(FIELD_EVENT_LOCATION_NAME, event.getLocation().getName(), Field.Store.NO));
      }

      if (event.getLocation().getPostalAddress() != null) {

        if (event.getLocation().getPostalAddress().getName() != null) {
          document.add(new TextField(FIELD_EVENT_LOCATION_POSTAL_ADDRESS_NAME, event.getLocation().getPostalAddress().getName(), Field.Store.NO));
        }
        if (event.getLocation().getPostalAddress().getStreetAddress() != null) {
          document.add(new TextField(FIELD_EVENT_LOCATION_POSTAL_ADDRESS_STREET_ADDRESS, event.getLocation().getPostalAddress().getStreetAddress(), Field.Store.NO));
        }
        if (event.getLocation().getPostalAddress().getPostalCode() != null) {
          document.add(new TextField(FIELD_EVENT_LOCATION_POSTAL_ADDRESS_POSTAL_CODE, event.getLocation().getPostalAddress().getPostalCode(), Field.Store.NO));
        }
        if (event.getLocation().getPostalAddress().getAddressLocality() != null) {
          document.add(new TextField(FIELD_EVENT_LOCATION_POSTAL_ADDRESS_POSTAL_TOWN, event.getLocation().getPostalAddress().getAddressLocality(), Field.Store.NO));
        }
        if (event.getLocation().getPostalAddress().getAddressCountry() != null) {
          document.add(new TextField(FIELD_EVENT_LOCATION_POSTAL_ADDRESS_COUNTRY, event.getLocation().getPostalAddress().getAddressCountry(), Field.Store.NO));
        }

      }

      if (event.getLocation().getGeo() != null) {
        event.getLocation().getGeo().accept(new GeoVisitor<Void>() {
          @Override
          public Void visit(GeoCoordinates geoCoordinates) {

            document.add(new DoubleField(FIELD_EVENT_LOCATION_GEO_LATITUDE, geoCoordinates.getLatitude(), StoredField.Store.NO));
            document.add(new DoubleField(FIELD_EVENT_LOCATION_GEO_LONGITUDE, geoCoordinates.getLongitude(), StoredField.Store.NO));

            // todo: future, index bboxes rather than coordinates in order to query for polygons.
//            document.add(new DoubleField(FIELD_EVENT_LOCATION_GEO_SOUTH_LATITUDE, geoCoordinates.getLatitude(), StoredField.Store.NO));
//            document.add(new DoubleField(FIELD_EVENT_LOCATION_GEO_WEST_LONGITUDE, geoCoordinates.getLongitude(), StoredField.Store.NO));
//            document.add(new DoubleField(FIELD_EVENT_LOCATION_GEO_NORTH_LATITUDE, geoCoordinates.getLatitude(), StoredField.Store.NO));
//            document.add(new DoubleField(FIELD_EVENT_LOCATION_GEO_EAST_LONGITUDE, geoCoordinates.getLongitude(), StoredField.Store.NO));

            return null;
          }
        });
      }

    }


    // combined text ngrams
    if (event.getName() != null) {
      document.add(new TextField(FIELD_EVENT_COMBINED_TEXT_NGRAMS, event.getName(), Field.Store.NO));
    }
    if (event.getDescription() != null) {
      document.add(new TextField(FIELD_EVENT_COMBINED_TEXT_NGRAMS, event.getDescription(), Field.Store.NO));
    }
    if (event.getTags() != null) {
      for (String tag : event.getTags()) {
        document.add(new TextField(FIELD_EVENT_COMBINED_TEXT_NGRAMS, tag, Field.Store.NO));
      }
    }


    return document;

  }

  private Document documentFactory(Event event, JSONObject json, Show show) throws Exception {

    Document document = documentFactory(event, json);

    document.add(new StringField(FIELD_EVENT_SHOW_STATUS, show.getStatus() != null ? show.getStatus().name() : ShowStatus.scheduled.name(), StoredField.Store.NO));

    document.add(new LongField(FIELD_EVENT_SHOW_START_DATE_TIME, show.getStartTimeEpochMilliseconds(), StoredField.Store.NO));
    if (show.getEndTimeEpochMilliseconds() != null) {
      document.add(new LongField(FIELD_EVENT_SHOW__END_DATE_TIME, show.getEndTimeEpochMilliseconds(), StoredField.Store.NO));
    } else {
      // todo: what sort of queries is required?
    }


    return document;

  }


  public SearchResults search(final SearchRequest searchRequest) throws Exception {

    /** Multiple index points for the same event, i.e. shows on multiple dates. */
    final Map<Long, SearchResult> indexResultsById = new HashMap<>();

    IndexSearcher indexSearcher = searcherManager.acquire();
    try {


      indexSearcher.search(searchRequest.getQuery(), new Collector() {
        @Override
        public LeafCollector getLeafCollector(final LeafReaderContext leafReaderContext) throws IOException {
          return new LeafCollector() {
            Scorer scorer;
            NumericDocValues identityValues;
            BinaryDocValues jsonValues;

            @Override
            public void setScorer(Scorer scorer) throws IOException {
              this.scorer = scorer;
            }

            @Override
            public void collect(int doc) throws IOException {
              if (identityValues == null) {
                identityValues = leafReaderContext.reader().getNumericDocValues(FIELD_EVENT_IDENTITY_VALUE);
              }

              long eventId = identityValues.get(doc);
              SearchResult searchResult = indexResultsById.get(eventId);
              if (searchResult == null) {
                searchResult = new SearchResult();
                searchResult.setEventId(eventId);
                indexResultsById.put(eventId, searchResult);
              }
              if (searchRequest.isEventJsonOutput() && searchResult.getJson() == null) {
                if (jsonValues == null) {
                  jsonValues = leafReaderContext.reader().getBinaryDocValues(FIELD_EVENT_JSON_VALUE);
                }
                searchResult.setJson(jsonValues.get(doc).utf8ToString());
              }
              if (searchRequest.isScoring()) {
                float score = scorer.score();
                if (searchResult.getScore() < score) {
                  searchResult.setScore(score);
                }
              }
            }
          };
        }

        @Override
        public boolean needsScores() {
          return true;
        }

      });


    } finally {
      searcherManager.release(indexSearcher);
    }


    List<SearchResult> orderedSearchResults = new ArrayList<>(indexResultsById.values());
    // todo sort order
    Collections.sort(orderedSearchResults, new Comparator<SearchResult>() {
      @Override
      public int compare(SearchResult o1, SearchResult o2) {
        return Float.compare(o2.getScore(), o1.getScore());
      }
    });

    SearchResults searchResults = new SearchResults();
    searchResults.setTotalNumberOfSearchResults(orderedSearchResults.size());
    searchResults.setSearchResults(new ArrayList<SearchResult>(searchRequest.getLimit()));
    for (int i = searchRequest.getStartIndex(); i < searchRequest.getLimit() + searchRequest.getStartIndex() && i < indexResultsById.size(); i++) {
      searchResults.getSearchResults().add(orderedSearchResults.get(i));
    }

    return searchResults;

  }


  public File getDataPath() {
    return dataPath;
  }

  public void setDataPath(File dataPath) {
    this.dataPath = dataPath;
  }


}
