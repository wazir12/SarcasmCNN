package io.rj93.sarcasm.cnn.channels;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.iterator.CnnSentenceDataSetIterator.UnknownWordHandling;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.json.JSONObject;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

public class WordVectorChannel extends Channel {
	
	Logger logger = LogManager.getLogger(WordVectorChannel.class);
	
	public static final String TYPE = "WordVectorChannel";
	private static final String UNKNOWN_WORD_SENTINEL = "UNK";
	
	private final File wordVectorFile;
	private final WordVectors wordVector;
	private final boolean useNormalizedWordVectors;
	private final UnknownWordHandling unknownWordHandling;
	private final int size;
	private final boolean sentencesAlongHeight = true;
	private final int maxSentenceLength;
	private TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
	private INDArray unknown;
	
	public WordVectorChannel(String wordVectorPath, boolean useNormalizedWordVectors, UnknownWordHandling unknownWordHandling, int maxSentenceLength){
		this(new File(wordVectorPath), useNormalizedWordVectors, unknownWordHandling, maxSentenceLength);
	}
	
	public WordVectorChannel(File wordVectorFile, boolean useNormalizedWordVectors, UnknownWordHandling unknownWordHandling, int maxSentenceLength){
		this.wordVectorFile = wordVectorFile;
		this.wordVector = readWordVector(wordVectorFile);
		this.wordVector.setUNK("UNK");
		this.size = wordVector.getWordVector(wordVector.vocab().wordAtIndex(0)).length;
		this.useNormalizedWordVectors = useNormalizedWordVectors;
		this.unknownWordHandling = unknownWordHandling;
		this.maxSentenceLength = maxSentenceLength;
	}

	private WordVectors readWordVector(File file){
		logger.info("Reading word vector: " + file.getAbsolutePath());
		return WordVectorSerializer.loadStaticModel(file);
	}
	
	@Override
	public int getSize() {
		return size;
	}
	
	@Override
	public INDArray getFeatureVector(String sentence) {
		return getFeatureVectors(Arrays.asList(sentence)).getFeatures();
	}
	
	@Override
	public MultiResult getFeatureVectors(List<String> sentences) {
		
		List<List<String>> tokenizedSentences = new ArrayList<>(sentences.size());
        int maxLength = -1;
        for (int i = 0; i < sentences.size(); i++) {
            List<String> tokens = tokenizeSentence(sentences.get(i));
            
            if(tokens.isEmpty()) // required to stop ND4JIllegalArgumentException
            	tokens.add(UNKNOWN_WORD_SENTINEL);
            
            maxLength = Math.max(maxLength, tokens.size());
            tokenizedSentences.add(tokens);
        }

        if (maxSentenceLength > 0 && maxLength > maxSentenceLength) {
            maxLength = maxSentenceLength;
        }

        int[] featuresShape = new int[4];
        featuresShape[0] = sentences.size();
        featuresShape[1] = 1;
        if (sentencesAlongHeight) {
            featuresShape[2] = maxLength;
            featuresShape[3] = size;
        } else {
            featuresShape[2] = size;
            featuresShape[3] = maxLength;
        }
        
        
        INDArrayIndex[] indices = new INDArrayIndex[4];
        indices[1] = NDArrayIndex.point(0);
        if (sentencesAlongHeight) {
            indices[3] = NDArrayIndex.all();
        } else {
            indices[2] = NDArrayIndex.all();
        }
        
        INDArray features = Nd4j.create(featuresShape);
        for (int i = 0; i < sentences.size(); i++) {
        	
            List<String> currSentence = tokenizedSentences.get(i);
            indices[0] = NDArrayIndex.point(i);
            
            for (int j = 0; j < currSentence.size() && j < maxSentenceLength; j++) {
                INDArray vector = getVector(currSentence.get(j));

                if (sentencesAlongHeight) {
                    indices[2] = NDArrayIndex.point(j);
                } else {
                    indices[3] = NDArrayIndex.point(j);
                }

                features.put(indices, vector);
            }
        }
        
        
        // create the feature mask
        INDArray featuresMask = Nd4j.create(sentences.size(), maxLength);
        for (int i = 0; i < sentences.size(); i++) {
            int sentenceLength = tokenizedSentences.get(i).size();
            if (sentenceLength >= maxLength) {
            	featuresMask.getRow(i).assign(1.0); // assign all cols in the row to have values
            } else {
            	featuresMask.get(NDArrayIndex.point(i), NDArrayIndex.interval(0, sentenceLength)).assign(1.0); // assign up to the sentence length to have values
            }
        }
		
		return new MultiResult(features, featuresMask, maxLength);
		
	}
	
	private INDArray getVector(String word) {
        INDArray vector;
        if (unknownWordHandling == UnknownWordHandling.UseUnknownVector && word == UNKNOWN_WORD_SENTINEL) {
            vector = unknown;
        } else {
            if (useNormalizedWordVectors) {
                vector = wordVector.getWordVectorMatrixNormalized(word);
            } else {
                vector = wordVector.getWordVectorMatrix(word);
            }
        }
        return vector;
    }
	
	/**
	 * tokenizes the sentence
	 * @param sentence
	 * @return the list of tokens
	 */
	private List<String> tokenizeSentence(String sentence) {
        Tokenizer t = tokenizerFactory.create(sentence);

        List<String> tokens = new ArrayList<>();
        while (t.hasMoreTokens()) {
            String token = t.nextToken();
            if (!wordVector.hasWord(token)) {
            	// how to handle unknown tokens
                switch (unknownWordHandling) {
                    case RemoveWord:
                        continue;
                    case UseUnknownVector:
                        token = UNKNOWN_WORD_SENTINEL;
                }
            }
            tokens.add(token);
        }
        return tokens;
    }
	
	public JSONObject getConfig(){
		JSONObject json = new JSONObject();
		
		json.put("type", TYPE);
		json.put("file", wordVectorFile.getAbsolutePath());
		json.put("maxSentenceLength", maxSentenceLength);
		json.put("useNormalizedWordVectors", useNormalizedWordVectors);
		json.put("unknownWordHandling", unknownWordHandling);
		
		return json;
	}

	public static Channel loadFromConfig(JSONObject config) {

		String path = config.getString("file");
		int maxSentenceLength = config.getInt("maxSentenceLength");
		boolean useNormalizedWordVectors = config.getBoolean("useNormalizedWordVectors");
		UnknownWordHandling unknownWordHandling;
		if (config.get("unknownWordHandling").equals("UseUnknownVector")){
			unknownWordHandling = UnknownWordHandling.UseUnknownVector;
		} else {
			unknownWordHandling = UnknownWordHandling.RemoveWord;
		}
		
		return new WordVectorChannel(path, useNormalizedWordVectors, unknownWordHandling, maxSentenceLength);
	}
	
	
	@Override
	public String toString(){
		return getConfig().toString();
	}

}
