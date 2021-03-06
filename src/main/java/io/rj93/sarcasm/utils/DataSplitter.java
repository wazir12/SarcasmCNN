package io.rj93.sarcasm.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.deeplearning4j.text.sentenceiterator.FileSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.iq80.leveldb.util.FileUtils;

import io.rj93.sarcasm.preprocessing.JSONPreProcessor;
import scala.collection.generic.BitOperations.Int;

public class DataSplitter {
	
	private float train;
	private float validation;
	private float test;
	private int seed;
	private Random rand;
	
	private List<String> trainSet = new ArrayList<String>();
	private List<String> validationSet = new ArrayList<String>();
	private List<String> testSet = new ArrayList<String>();
	
	public DataSplitter(){
		train = 0.6f;
		validation = 0.2f;
		test = 0.2f;
		seed = 123;
		rand = new Random(seed);
	}
	
	public DataSplitter(float train, float validation, float test, int seed){
		if (train + validation + test != 1)
			throw new IllegalArgumentException("The sum of train, validation, and test must equal 1");
		
		this.train = train;
		this.validation = validation;
		this.test = test;
		this.seed = seed;
		rand = new Random(seed);
	}
	
	public List<String> getTrainSet(){
		return trainSet;
	}
	
	public List<String> getValidationSet(){
		return validationSet;
	}
	
	public List<String> getTestSet(){
		return testSet;
	}
	
	/**
	 * splits the list into train, validation, and testing lists
	 * @param data
	 */
	public void split(List<String> data){
		for (String s : data){
			place(s);
		}
	}
	
	/**
	 * splits the iter into train, validation, and testing lists
	 * @param iter
	 */
	public void split(SentenceIterator iter){
		while(iter.hasNext()){
			place(iter.nextSentence());
		}
	}
	
	/**
	 * puts the string into either train, validation, or test list depending on the random float generated 
	 * @param s
	 */
	private void place(String s){
		float f = rand.nextFloat();
		if (f < train){
			trainSet.add(s);
		} else if (f < (train + validation)){
			validationSet.add(s);
		} else if (f < (train + validation + test)){
			testSet.add(s);
		} 
	}
	
	/**
	 * writes the data to file
	 * @param filePath the path to the output file
	 * @param data the list of data to write
	 * @param minSize the minimum sentence size
	 * @param maxSize the maximum sentence size
	 * @return if writing was successful
	 */
	private static boolean writeToFile(String filePath, List<String> data, int minSize, int maxSize){
		boolean success = false;
		
		File f = new File(filePath);
		f.getParentFile().mkdirs();
		
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(f);
			for (String s : data){
				if (s != null && s.length() >= minSize && s.length() <= maxSize){
					writer.println(s);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			try { writer.close(); } catch (Exception e) { /* ignored */ }
		}
		
		return success;
	}
	
	public static void main(String[] args){
		File inputDir = new File(DataHelper.REDDIT_FILTERED_DIR);

		File[] years = inputDir.listFiles((FileFilter) FileFilterUtils.directoryFileFilter()); // only list directories
		for (File year : years){
			for (String Class : new String[]{"pos", "neg"}){

				List<File> files = FileUtils.listFiles(new File(year.getAbsolutePath() + "/" + Class));
				for (File f : files){
					
					// read and preprocess the files
					List<String> processedStrings = new ArrayList<String>();
					FileSentenceIterator iter = new FileSentenceIterator(f);
					iter.setPreProcessor(new JSONPreProcessor(false, false));
					while (iter.hasNext()){
						String s = iter.nextSentence();
						processedStrings.add(s);
					}
					
					DataSplitter splitter = new DataSplitter(0.8f, 0f, 0.2f, 123);
					splitter.split(processedStrings); // split into 80% train, 20% test
					
					List<String> train = splitter.getTrainSet();
					List<String> val = splitter.getValidationSet();
					List<String> test = splitter.getTestSet();
					
					float totalSize = train.size() + val.size() + test.size();

					float trainPer = train.size() / totalSize;
					float valPer = val.size() / totalSize;
					float testPer = test.size() / totalSize;
					
					String outDir = DataHelper.PREPROCESSED_DATA_DIR + year.getName() + "/";
					System.out.println(String.format("train = %f, val = %f, test = %f, dir = %s", trainPer, valPer, testPer, year.getName() + "/" +Class));
					
					// write lists to file
					int minSize = 2;
					int maxSize = Integer.MAX_VALUE;
					writeToFile(outDir + "train/" + Class + "/" + f.getName(), train, minSize, maxSize);
					writeToFile(outDir + "test/" + Class + "/" + f.getName(), test, minSize, maxSize);
					
				}
				
			}
		}
	}
	
}
