package net.oschina.search;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.*;
import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 搜索工具类
 * User: Winter Lau
 * Date: 13-1-10
 * Time: 上午11:54
 */
public class SearchHelper {

    private final static Log log = LogFactory.getLog(SearchHelper.class);
    private final static IKAnalyzer analyzer = new IKAnalyzer();
    private final static Formatter highlighter_formatter = new SimpleHTMLFormatter("<span class=\"highlight\">","</span>");

    private final static String FN_ID = "___id";
    private final static String FN_CLASSNAME = "___class";

    private final static List<String> nowords = new ArrayList<String>(){{
        try{
            addAll(IOUtils.readLines(SearchHelper.class.getResourceAsStream("/stopword.dic")));
        }catch(IOException e){
            log.error("Unabled to read stopword file", e);
        }
    }};

    /**
     * 关键字切分
     * @param sentence 要分词的句子
     * @return 返回分词结果
     */
    public static List<String> splitKeywords(String sentence) {

        List<String> keys = new ArrayList<String>();

        if(StringUtils.isNotBlank(sentence))  {
            StringReader reader = new StringReader(sentence);
            IKSegmenter ikseg = new IKSegmenter(reader, true);
            try{
                do{
                    Lexeme me = ikseg.next();
                    if(me == null)
                        break;
                    String term = me.getLexemeText();
                    if(term.length() == 1)
                        continue;
                    if(StringUtils.isNumeric(StringUtils.remove(term,'.')))
                        continue;
                    if(nowords.contains(term.toLowerCase()))
                        continue;
                    keys.add(term);
                }while(true);
            }catch(IOException e){
                log.error("Unable to split keywords", e);
            }
        }

        return keys;
    }

    /**
     * 对一段文本执行语法高亮处理
     * @param text 要处理高亮的文本
     * @param key 高亮的关键字
     * @return 返回格式化后的HTML文本
     */
    public static String highlight(String text, String key) {
        if(StringUtils.isBlank(key) || StringUtils.isBlank(text))
            return text;
        String result = null;
        try {
            key = QueryParser.escape(key.trim().toLowerCase());
            QueryScorer scorer = new QueryScorer(new TermQuery(new Term(null,QueryParser.escape(key))));
            Highlighter hig = new Highlighter(highlighter_formatter, scorer);
            TokenStream tokens = analyzer.tokenStream(null, new StringReader(text));
            result = hig.getBestFragment(tokens, text);
        } catch (Exception e) {
            log.error("Unabled to hightlight text", e);
        }
        return (result != null)?result:text;
    }
    
    /**
     * 返回文档中保存的对象 id
     * @param doc
     * @return
     */
    public static long docid(Document doc) {
    	return NumberUtils.toLong(doc.get(FN_ID), 0);
    }

    /**
     * 将对象转成 Lucene 的文档
     * @param obj Java 对象
     * @return 返回Lucene文档
     */
    public static Document obj2doc(Searchable obj) {
        if(obj == null)
            return null;

        Document doc = new Document();
        doc.add(new StoredField(FN_ID, obj.id()));
        doc.add(new StoredField(FN_CLASSNAME, obj.getClass().getName()));

        //存储字段
        List<String> fields = obj.storeFields();
        if(fields != null)
            for(String fn : fields) {
                Object fv = readField(obj, fn);
                doc.add(obj2field(fn, fv, true));
            }

        //索引字段
        fields = obj.indexFields();
        if(fields != null)
            for(String fn : fields) {
                String fv = (String)readField(obj, fn);
                doc.add(new TextField(fn, fv, Field.Store.NO));
            }

        //扩展存储字段
        Map<String, String> eDatas = obj.extendStoreDatas();
        if(eDatas != null)
            for(String fn : eDatas.keySet()){
                String fv = eDatas.get(fn);
                doc.add(obj2field(fn, fv, true));
            }

        //扩展索引字段
        eDatas = obj.extendIndexDatas();
        if(eDatas != null)
            for(String fn : eDatas.keySet()){
                String fv = eDatas.get(fn);
                doc.add(new TextField(fn, fv, Field.Store.NO));
            }

        return doc;
    }

    /**
     * 访问对象某个属性的值
     *
     * @param obj 对象
     * @param field 属性名
     * @return  Lucene 文档字段
     */
    private static Object readField(Object obj, String field) {
        try {
            return PropertyUtils.getProperty(obj, field);
        } catch (Exception e) {
            log.error("Unabled to get property '"+field+"' of " + obj.getClass().getSimpleName(), e);
            return null;
        }

    }

    private static Field obj2field(String field, Object fieldValue, boolean store) {
        if (fieldValue instanceof Date) //日期
            return new LongField(field, ((Date)fieldValue).getTime(), store?Field.Store.YES:Field.Store.NO);
        if (fieldValue instanceof Long) //长整数
            return new LongField(field, ((Number)fieldValue).longValue(), store?Field.Store.YES:Field.Store.NO);
        if (fieldValue instanceof Float) //浮点数
            return new FloatField(field, ((Number)fieldValue).floatValue(), store?Field.Store.YES:Field.Store.NO);
        if (fieldValue instanceof Number) //其他数值
            return new IntField(field, ((Number)fieldValue).intValue(), store?Field.Store.YES:Field.Store.NO);
        //其他默认当字符串处理
        return new StringField(field, (String)fieldValue, store?Field.Store.YES:Field.Store.NO);
    }

}
