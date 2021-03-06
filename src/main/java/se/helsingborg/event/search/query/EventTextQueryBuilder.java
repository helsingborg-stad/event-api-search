package se.helsingborg.event.search.query;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import se.helsingborg.event.search.EventIndexAnalyzerBuilder;
import se.helsingborg.event.search.IndexManager;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author kalle
 * @since 2015-11-05 22:47
 */
public class EventTextQueryBuilder {

  private Analyzer analyzer;

  private float tieBreakerMultiplier = 0f;

  private float nameBoost = 3f;
  private float descriptionBoost = 1f;
  private float tagBoost = 2f;
  private float combinedTextNgramBoost = 0.1f;


  private String text;

  private DisjunctionMaxQuery disjunctionMaxQuery;


  public Query build() throws Exception {

    if (analyzer == null) {
      analyzer = new EventIndexAnalyzerBuilder().build();
    }

    disjunctionMaxQuery = new DisjunctionMaxQuery(tieBreakerMultiplier);

    tagQueryFactory();
    nameQueryFactory();
    descriptionQueryFactory();

    combinedTextNgramQueryFactory();

    // todo location text
    // todo offer text


    return disjunctionMaxQuery;
  }


  private void nameQueryFactory() throws Exception {
    textQueryFactory(IndexManager.FIELD_EVENT_NAME, nameBoost);
  }

  private void descriptionQueryFactory() throws Exception {
    textQueryFactory(IndexManager.FIELD_EVENT_DESCRIPTION, descriptionBoost);
  }


  private void textQueryFactory(String field, float boost) throws Exception {

    textTokenQueryFactory(field, boost);
    // todo multiple phrases with low slop, order and configurable boost factors
    textPhraseQueryFactory(field, boost, Integer.MAX_VALUE, false);


  }

  private void textTokenQueryFactory(String field, float boost) throws IOException {
    TokenStream ts = analyzer.tokenStream(field, new StringReader(text));
    try {
      ts.reset();
      CharTermAttribute charTermAttribute = ts.addAttribute(CharTermAttribute.class);

      while (ts.incrementToken()) {
        String token = charTermAttribute.toString();
        TermQuery termQuery = new TermQuery(new Term(field, token));
        termQuery.setBoost(boost);
        disjunctionMaxQuery.add(termQuery);
      }
    } finally {
      ts.close();
    }
  }

  private void textPhraseQueryFactory(String field, float boost, int slop, boolean ordered) throws IOException {

    int tokensCount = 0;

    SpanNearQuery.Builder phraseQuery = new SpanNearQuery.Builder(field, ordered);
    phraseQuery.setSlop(slop);

    TokenStream ts = analyzer.tokenStream(field, new StringReader(text));
    try {
      ts.reset();
      CharTermAttribute charTermAttribute = ts.addAttribute(CharTermAttribute.class);

      while (ts.incrementToken()) {
        String token = charTermAttribute.toString();
        SpanTermQuery termQuery = new SpanTermQuery(new Term(field, token));
        phraseQuery.addClause(termQuery);
        tokensCount++;
      }
    } finally {
      ts.close();
    }

    if (tokensCount < 2) {
      return;
    }

    Query query = phraseQuery.build();
    query.setBoost(boost);
    disjunctionMaxQuery.add(query);
  }

  private void tagQueryFactory() throws Exception {

    tagTokenQueryFactory();
    tagShingleTokenQueryFactory();

  }

  private void tagShingleTokenQueryFactory() throws IOException {
    /**
     * add shingle tokens
     * "dunkel kulturhus helsingborg" ->
     * "dunkel kulturhus",
     * "kulturhus helsingborg"
     * "dunkel kulturhus helsingborg"
     */

    // todo whitespace analyzer?
    TokenStream ts = new StandardAnalyzer().tokenStream(IndexManager.FIELD_EVENT_TAG, text);
    LowerCaseFilter lowerCaseFilter = new LowerCaseFilter(ts);

    ShingleFilter shingleFilter = new ShingleFilter(lowerCaseFilter);
    shingleFilter.setFillerToken(" ");
    shingleFilter.setMinShingleSize(2);
    shingleFilter.setMaxShingleSize(3);
    shingleFilter.setOutputUnigrams(false);
    shingleFilter.reset();

    CharTermAttribute charTermAttribute = shingleFilter.addAttribute(CharTermAttribute.class);

    while (shingleFilter.incrementToken()) {
      String token = charTermAttribute.toString();
      TermQuery termQuery = new TermQuery(new Term(IndexManager.FIELD_EVENT_TAG, token));
      termQuery.setBoost(tagBoost);
      disjunctionMaxQuery.add(termQuery);
    }

    shingleFilter.close();
  }

  private void tagTokenQueryFactory() throws IOException {
    /**
     * add non shingle tokens
     * "dunkel kulturhus helsingorg" ->
     * "dunkel",
     * "kulturhus",
     * "helsingborg"
     */

    // todo whitespace analyzer?
    TokenStream ts = new StandardAnalyzer().tokenStream(IndexManager.FIELD_EVENT_TAG, text);
    LowerCaseFilter lowerCaseFilter = new LowerCaseFilter(ts);
    lowerCaseFilter.reset();

    CharTermAttribute charTermAttribute = lowerCaseFilter.addAttribute(CharTermAttribute.class);

    while (lowerCaseFilter.incrementToken()) {
      TermQuery termQuery = new TermQuery(new Term(IndexManager.FIELD_EVENT_TAG, charTermAttribute.toString()));
      termQuery.setBoost(tagBoost);
      disjunctionMaxQuery.add(termQuery);
    }

    lowerCaseFilter.close();
  }

  private void combinedTextNgramQueryFactory() throws IOException {
    TokenStream ts = new StandardAnalyzer().tokenStream(IndexManager.FIELD_EVENT_COMBINED_TEXT_NGRAMS, text);
    LowerCaseFilter lowerCaseFilter = new LowerCaseFilter(ts);
    NGramTokenFilter nGramTokenFilter = new NGramTokenFilter(lowerCaseFilter, 3, 5);
    nGramTokenFilter.reset();

    CharTermAttribute charTermAttribute = nGramTokenFilter.addAttribute(CharTermAttribute.class);

    while (nGramTokenFilter.incrementToken()) {
      TermQuery termQuery = new TermQuery(new Term(IndexManager.FIELD_EVENT_COMBINED_TEXT_NGRAMS, charTermAttribute.toString()));
      termQuery.setBoost(combinedTextNgramBoost);
      disjunctionMaxQuery.add(termQuery);
    }

    nGramTokenFilter.close();

  }


  public String getText() {
    return text;
  }

  public EventTextQueryBuilder setText(String text) {
    this.text = text;
    return this;
  }

  public float getTieBreakerMultiplier() {
    return tieBreakerMultiplier;
  }

  public EventTextQueryBuilder setTieBreakerMultiplier(float tieBreakerMultiplier) {
    this.tieBreakerMultiplier = tieBreakerMultiplier;
    return this;
  }




}
