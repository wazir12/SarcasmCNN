package io.rj93.sarcasm.preprocessing;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.rj93.sarcasm.utils.DataHelper;
import io.rj93.sarcasm.utils.PrettyTime;

public class FilterFromRedditRaw implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(FilterFromRedditRaw.class);
	
	private int id;
	private String inFilePath;
	private String outDir;
	
	public FilterFromRedditRaw(int id, String inFile, String outDir){
		this.id = id;
		this.inFilePath = inFile;
		this.outDir = outDir;
	}
	
	/**
	 * decompresses a bz2 file
	 * @param inPath path to the bz2 file 
	 * @param outPath path to save the bz2 file to
	 * @return if it was successful
	 */
	private static boolean decompressFile(String inPath, String outPath){
		boolean success = false;
		
		FileInputStream fin = null;
		BufferedInputStream in = null;
		FileOutputStream out = null;
		BZip2CompressorInputStream bzIn = null;
		try {
			fin = new FileInputStream(inPath);
			in = new BufferedInputStream(fin);
			out = new FileOutputStream(outPath);
			bzIn = new BZip2CompressorInputStream(in);
			final byte[] buffer = new byte[1024];
			int n = 0;
			while (-1 != (n = bzIn.read(buffer))) {
			    out.write(buffer, 0, n);
			}
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try { fin.close(); } catch (Exception e) { /* ignored */ }
			try { in.close(); } catch (Exception e) { /* ignored */ }
			try { out.close(); } catch (Exception e) { /* ignored */ }
			try { bzIn.close(); } catch (Exception e) { /* ignored */ }
		}
		if (success){
			logger.info("decompressFile: " + inPath + " successful");
		} else {
			logger.error("decompressFile: " + inPath + " failed!");
		}
		return success;
	}
	
	/**
	 * deletes a file
	 * @param path path to file
	 * @return if it was successful
	 */
	private static boolean deleteFile(String path){
		File f = new File(path);
		return f.delete();
	}
	
	/**
	 * searches through the uncompressed reddit data to build the sarcastic and non-sarcastic datasets
	 * @param inFilePath uncompressed reddit data file path
	 * @param outDirPath the dir to save the datasets to
	 */
	private static void filter(String inFilePath, String outDirPath){
		File inFile = new File(inFilePath);

		String outPositivePath = outDirPath + "pos/";
		String outNegativePath = outDirPath + "neg/";
		File outPositiveFile = new File(outPositivePath);
		File outNegativeFile = new File(outNegativePath);
		outPositiveFile.mkdirs();
		outNegativeFile.mkdirs();
		
		List<Integer> sarcasticSizes = new ArrayList<Integer>();
		
		BufferedReader br = null;
		PrintWriter positiveWriter = null;
		PrintWriter negativeWriter = null;
		int posCount = 0;
		int negCount = 0;
		int count = 0;
		try {
			br = new BufferedReader(new FileReader(inFile));
			positiveWriter = new PrintWriter(outPositivePath + inFile.getName(), "UTF-8");
			negativeWriter = new PrintWriter(outNegativePath + inFile.getName(), "UTF-8");
			
			String line;
		    while ((line = br.readLine()) != null) {
		    	count++;
		    	JSONObject comment = new JSONObject(line);
		    	String body = comment.getString("body");
		    	
		    	if (body.endsWith(" /s")){ // body is sarcastic
		    		sarcasticSizes.add(body.length());
		    		positiveWriter.println(line);
		    		posCount++;
		    	} else if (sarcasticSizes.size() > 0){ // search for similar sized non-sarcastic comments
		    		for (int i = 0; i < sarcasticSizes.size(); i++){
		    			int size = sarcasticSizes.get(i);
		    			if (Math.abs(body.length() - size) <= (0.1 * size)){ // compare sizes to be within 10% of each other
		    				negativeWriter.println(line);
		    				sarcasticSizes.remove(i);
		    				negCount++;
		    				break;
		    			}
		    		}
		    	}
		    }

			logger.info("filter: {} completed, pos = {}, neg = {}, total = {}", inFilePath, posCount, negCount, count);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try { br.close(); } catch (Exception e) { /* ignored */ }
			try { positiveWriter.close(); } catch (Exception e) { /* ignored */ }
			try { negativeWriter.close(); } catch (Exception e) { /* ignored */ }
		}
		
	}
	
	@Override
	public void run() {
		long start = System.nanoTime();
		logger.info("Thread {} - Decompressing: {}", id, inFilePath);
		String decompressedFilePath = inFilePath.replace("bz2", "json");

		if (decompressFile(inFilePath, decompressedFilePath)){
			logger.info("Thread {} - Completed decompressing: {}", id, inFilePath);
			
			filter(decompressedFilePath, outDir);
			logger.info("Thread {} - Completed filtering: {}", id, decompressedFilePath);
			
			deleteFile(decompressedFilePath);
		}
		long diff = System.nanoTime()- start;
		logger.info("Thread {} - finished in {}", id, PrettyTime.prettyNano(diff));
	}
	
	public static void main(String[] args) {
		File redditDataDir = new File(DataHelper.REDDIT_RAW_DIR);
		
		try {
			List<File> compressedFiles = DataHelper.getFilesFromDir(redditDataDir, true, new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".bz2");
				}
			});
			
			ExecutorService executor = Executors.newFixedThreadPool(4);
			int id = 0;
			for (File f : compressedFiles){
				String outputDir = DataHelper.REDDIT_FILTERED_DIR + f.getParentFile().getName() + "/";
				executor.execute(new FilterFromRedditRaw(id++, f.getAbsolutePath(), outputDir));
			}
			executor.shutdown();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} 
		
		

	}

}
