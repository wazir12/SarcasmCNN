package io.rj93.sarcasm.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DataHelper {
	
	public final static String DATA_DIR = getDataDir();
	public final static String REDDIT_DATA_DIR = DATA_DIR + "reddit_data/";
	public final static String SORTED_DATA_DIR = DATA_DIR + "output_data/";
	public final static String PREPROCESSED_DATA_DIR = DATA_DIR + "preprocessed_data/";
	public final static String TRAIN_DATA_DIR = DATA_DIR + "train/";
	public final static String EVAL_DATA_DIR = PREPROCESSED_DATA_DIR + "eval/";
	public final static String TEST_DATA_DIR = PREPROCESSED_DATA_DIR + "test/";
	public final static String WORD2VEC_DIR = DATA_DIR + "word2vec/";
	
	public final static String GOOGLE_NEWS_WORD2VEC = WORD2VEC_DIR + "google/GoogleNews-vectors-negative300.bin";
	
	private static String getDataDir(){
		String dir = null;
		if (System.getProperty("os.name").contains("Windows")){
			dir = "C:/Users/Richard/Documents/";
		} else {
			dir = "/Users/richardjones/Documents/";
		}
		dir += "Project/data/";
		return dir;
	}
	
	public static List<File> getFilesFromDir(String dir) throws FileNotFoundException {
		return getFilesFromDir(dir, false);
	}
	
	public static List<File> getFilesFromDir(String dir, boolean recursive) throws FileNotFoundException {
		return getFilesFromDir(new File(dir), true);
	}
	
	public static List<File> getFilesFromDir(File dir) throws FileNotFoundException {
		return getFilesFromDir(dir, false);
	}
	
	public static List<File> getFilesFromDir(File dir, boolean recursive) throws FileNotFoundException {
		
		if (!dir.exists() || !dir.isDirectory())
			throw new FileNotFoundException("Directory '" + dir.getAbsolutePath() + "' either does not exist, or is not a directory");
		
		List<File> files = new ArrayList<File>();
		for (File f : dir.listFiles()) {
			if (f.isFile())
				files.add(f);
			else if (recursive)
				files.addAll(getFilesFromDir(f, true));
		}
		
		return files;
	}
	
	public static void seperateToMultipleFiles(File inFile, File outDir) throws IOException{
		outDir.mkdirs();
		List<String> s = Files.readAllLines(Paths.get(inFile.getAbsolutePath()));
		int count = 0;
		for (int i = 0; i < s.size(); i++){
			String line = s.get(i);
			if (line.length() > 3){
				PrintWriter writer = new PrintWriter(outDir.getAbsolutePath() + "/" + i + ".json", "UTF-8");
				writer.println(line);
				writer.close();
			} else {
				count++;
			}
		}
		System.out.println("Skipped " + count + " files");
	}

}