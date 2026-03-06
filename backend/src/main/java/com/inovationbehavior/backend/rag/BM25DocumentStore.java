package com.inovationbehavior.backend.rag;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BM25 文档存储，与向量库并行用于多路召回。
 * 使用 Lucene 中文分词（SmartChineseAnalyzer），索引与检索均在内存（可改为 FSDirectory）。
 */
@Component
public class BM25DocumentStore {

    public static final String FIELD_ID = "id";
    public static final String FIELD_CONTENT = "content";

    private final Directory directory = new ByteBuffersDirectory();
    private final Analyzer analyzer = new StandardAnalyzer();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public void addDocuments(List<Document> documents) throws IOException {
        rwLock.writeLock().lock();
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (int i = 0; i < documents.size(); i++) {
                    Document doc = documents.get(i);
                    org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
                    String id = "bm25_" + i + "_" + System.identityHashCode(doc);
                    luceneDoc.add(new StringField(FIELD_ID, id, Field.Store.YES));
                    luceneDoc.add(new TextField(FIELD_CONTENT, doc.getText() != null ? doc.getText() : "", Field.Store.YES));
                    writer.addDocument(luceneDoc);
                }
                writer.commit();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * BM25 检索，返回带分数的文档列表（分数仅用于 RRF，不保证与 Lucene 原始分一致）。
     */
    public List<ScoredDocument> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        rwLock.readLock().lock();
        try {
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
                TopDocs topDocs = searcher.search(parser.parse(QueryParser.escape(query)), topK);
                List<ScoredDocument> result = new ArrayList<>();
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    org.apache.lucene.document.Document luceneDoc = searcher.doc(sd.doc);
                    String content = luceneDoc.get(FIELD_CONTENT);
                    result.add(new ScoredDocument(
                            new Document(content, Map.of("source", "bm25", "score", (double) sd.score)),
                            sd.score
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            return List.of();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void clear() throws IOException {
        rwLock.writeLock().lock();
        try {
            try (IndexWriter w = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                w.deleteAll();
                w.commit();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public record ScoredDocument(Document document, float score) {}
}
