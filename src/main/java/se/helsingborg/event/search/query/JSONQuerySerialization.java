package se.helsingborg.event.search.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import se.helsingborg.event.search.IndexManager;

/**
 * @author kalle
 * @since 2014-09-10 18:20
 */
public class JSONQuerySerialization {

  private Query parseBoost(JSONObject jsonQuery, Query query) throws JSONException {
    if (jsonQuery.has("boost")) {
      double boost = jsonQuery.getDouble("boost");
      query.setBoost((float) boost);
    }
    return query;
  }

  public Query parse(JSONObject jsonQuery) throws Exception {

    String type = jsonQuery.getString("type");
    if ("boolean query".equalsIgnoreCase(type)) {

      BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

      JSONArray jsonClauses = jsonQuery.getJSONArray("clauses");

      for (int i = 0; i < jsonClauses.length(); i++) {

        JSONObject jsonClause = jsonClauses.getJSONObject(i);

        BooleanClause.Occur occur;
        String jsonOccur = jsonClause.getString("occur");
        if (jsonOccur.equalsIgnoreCase("must")) {
          occur = BooleanClause.Occur.MUST;
        } else if (jsonOccur.equalsIgnoreCase("should")) {
          occur = BooleanClause.Occur.SHOULD;
        } else if (jsonOccur.equalsIgnoreCase("must not")) {
          occur = BooleanClause.Occur.MUST_NOT;
        } else {
          throw new IllegalArgumentException("Expected occurs with value 'must', 'should' or 'must not', but was '" + jsonOccur + "'.");
        }

        booleanQuery.add(new BooleanClause(parse(jsonClause.getJSONObject("query")), occur));

      }

      return parseBoost(jsonQuery, booleanQuery.build());

    } else if ("match all documents".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, new MatchAllDocsQuery());

    } else if ("term".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, new TermQuery(new Term(jsonQuery.getString("field"), jsonQuery.getString("value"))));


    } else if ("coordinate envelope".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, new CoordinateEnvelopeQueryFactory()
          .setLatitudeField(jsonQuery.getString("latitudeField"))
          .setLongitudeField(jsonQuery.getString("longitudeField"))

          .setSouth(jsonQuery.getDouble("southLatitude"))
          .setWest(jsonQuery.getDouble("westLongitude"))
          .setNorth(jsonQuery.getDouble("northLatitude"))
          .setEast(jsonQuery.getDouble("eastLongitude"))
          .build());

    } else if ("coordinate circle envelope".equalsIgnoreCase(type)) {


      return parseBoost(jsonQuery, new CoordinateCircleEnvelopeQueryFactory()
          .setLatitudeField(jsonQuery.getString("latitudeField"))
          .setLongitudeField(jsonQuery.getString("longitudeField"))

          .setCentroidLatitude(jsonQuery.getDouble("centroidLatitude"))
          .setCentroidLongitude(jsonQuery.getDouble("centroidLongitude"))
          .setRadiusKilometers(jsonQuery.getDouble("radiusKilometers"))
          .build());


    } else if ("integer range".equalsIgnoreCase(type)) {

      int minimum = !jsonQuery.isNull("minimum") ? jsonQuery.getInt("minimum") : Integer.MIN_VALUE;
      boolean includeMinimum = !jsonQuery.has("includeMinimum") || jsonQuery.getBoolean("includeMinimum");

      int maximum = !jsonQuery.isNull("maximum") ? jsonQuery.getInt("maximum") : Integer.MAX_VALUE;
      boolean includeMaximum = !jsonQuery.has("includeMaximum") || jsonQuery.getBoolean("includeMaximum");

      return parseBoost(jsonQuery, NumericRangeQuery.newIntRange(jsonQuery.getString("field"), minimum, maximum, includeMinimum, includeMaximum));

    } else if ("long range".equalsIgnoreCase(type)) {

      long minimum = !jsonQuery.isNull("minimum") ? jsonQuery.getLong("minimum") : Long.MIN_VALUE;
      boolean includeMinimum = !jsonQuery.has("includeMinimum") || jsonQuery.getBoolean("includeMinimum");

      long maximum = !jsonQuery.isNull("maximum") ? jsonQuery.getLong("maximum") : Long.MAX_VALUE;
      boolean includeMaximum = !jsonQuery.has("includeMaximum") || jsonQuery.getBoolean("includeMaximum");

      return parseBoost(jsonQuery, NumericRangeQuery.newLongRange(jsonQuery.getString("field"), minimum, maximum, includeMinimum, includeMaximum));

    } else if ("float range".equalsIgnoreCase(type)) {

      float minimum = !jsonQuery.isNull("minimum") ? (float) jsonQuery.getDouble("minimum") : Float.MIN_VALUE;
      boolean includeMinimum = !jsonQuery.has("includeMinimum") || jsonQuery.getBoolean("includeMinimum");

      float maximum = !jsonQuery.isNull("maximum") ? (float) jsonQuery.getDouble("maximum") : Float.MAX_VALUE;
      boolean includeMaximum = !jsonQuery.has("includeMaximum") || jsonQuery.getBoolean("includeMaximum");

      return parseBoost(jsonQuery, NumericRangeQuery.newFloatRange(jsonQuery.getString("field"), minimum, maximum, includeMinimum, includeMaximum));

    } else if ("double range".equalsIgnoreCase(type)) {

      double minimum = !jsonQuery.isNull("minimum") ? jsonQuery.getDouble("minimum") : Double.MIN_VALUE;
      boolean includeMinimum = !jsonQuery.has("includeMinimum") || jsonQuery.getBoolean("includeMinimum");

      double maximum = !jsonQuery.isNull("maximum") ? jsonQuery.getDouble("maximum") : Double.MAX_VALUE;
      boolean includeMaximum = !jsonQuery.has("includeMaximum") || jsonQuery.getBoolean("includeMaximum");

      return parseBoost(jsonQuery, NumericRangeQuery.newDoubleRange(jsonQuery.getString("field"), minimum, maximum, includeMinimum, includeMaximum));

/*
 *  EVENT-API Specific queries
 *
 */

    } else if ("event text".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, new EventTextQueryBuilder().setText(jsonQuery.getString("text")).build());

    } else if ("event tags".equalsIgnoreCase(type)) {

      BooleanQuery.Builder query = new BooleanQuery.Builder();
      JSONArray values = jsonQuery.getJSONArray("values");
      for (int i = 0; i < values.length(); i++) {
        query.add(new BooleanClause(new TermQuery(new Term(IndexManager.FIELD_EVENT_TAG, values.getString(i).toUpperCase())), BooleanClause.Occur.MUST));
      }
      return parseBoost(jsonQuery, query.build());

    } else if ("future events".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, NumericRangeQuery.newLongRange(IndexManager.FIELD_EVENT_SHOW_START_DATE_TIME, System.currentTimeMillis(), Long.MAX_VALUE, false, true));

    } else if ("past events".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, NumericRangeQuery.newLongRange(IndexManager.FIELD_EVENT_SHOW_START_DATE_TIME, Long.MIN_VALUE, System.currentTimeMillis(), true, false));

    } else if ("event location coordinate envelope".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, new CoordinateEnvelopeQueryFactory()
          .setLatitudeField(IndexManager.FIELD_EVENT_LOCATION_GEO_LATITUDE)
          .setLongitudeField(IndexManager.FIELD_EVENT_LOCATION_GEO_LONGITUDE)

          .setSouth(jsonQuery.getDouble("southLatitude"))
          .setWest(jsonQuery.getDouble("westLongitude"))
          .setNorth(jsonQuery.getDouble("northLatitude"))
          .setEast(jsonQuery.getDouble("eastLongitude"))
          .build());

    } else if ("event location coordinate circle envelope".equalsIgnoreCase(type)) {

      return parseBoost(jsonQuery, new CoordinateCircleEnvelopeQueryFactory()
          .setLatitudeField(IndexManager.FIELD_EVENT_LOCATION_GEO_LATITUDE)
          .setLongitudeField(IndexManager.FIELD_EVENT_LOCATION_GEO_LONGITUDE)

          .setCentroidLatitude(jsonQuery.getDouble("centroidLatitude"))
          .setCentroidLongitude(jsonQuery.getDouble("centroidLongitude"))
          .setRadiusKilometers(jsonQuery.getDouble("radiusKilometers"))
          .build());


    } else {
      throw new IllegalArgumentException("Unsupported query type '" + type + "'.");
    }

  }


}
