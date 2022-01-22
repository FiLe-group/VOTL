package bot.utils.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import ch.qos.logback.classic.Logger;
import bot.App;
import bot.constants.Constants;

class KeyIsNull extends Exception {
	public KeyIsNull() {
		super();
	}
}

public class FileManager {
	
	private final Logger logger;
	private Map<String, File> files;
	private List<String> languages;

	public FileManager(App bot) {
		this.logger = bot.getLogger();
	}

	public FileManager addFile(String name, String internal, String external){
		createOrLoad(name, internal, external);
		
		return this;
	}
	
	// Convenience method do add new languages more easy.
	public FileManager addLang(String file) {
		if (languages == null)
			languages = new ArrayList<>();
		
		languages.add(file.toLowerCase());

		return addFile(file.toLowerCase(), Constants.LANG_DIR + file + ".json", Constants.DATA_PATH + Constants.LANG_DIR + file + ".json");
	}
	
	public Map<String, File> getFiles() {
		return files;
	}
	
	public List<String> getLanguages() {
		return languages;
	}
	
	public void createOrLoad(String name, String internal, String external) {
		if (files == null)
			files = new HashMap<>();

		File file = new File(external);
		String[] split = external.split("/");

		try {
			if (!file.exists()) {
				if ((split.length == 2 && !split[0].equals(".")) || (split.length >= 3 && split[0].equals("."))) {
					if (!file.getParentFile().mkdirs() && !file.getParentFile().exists()) {
						logger.warn("Failed to create directory {}", split[1]);
						return;
					}
				}
				if (file.createNewFile()) {
					if (export(App.class.getResourceAsStream(internal), external)) {
						logger.info("Successfully created {}!", name);
						files.put(name, file);
					} else {
						logger.warn("Failed to create {}!", name);
					}
				}
			} else {
				logger.info("Successfully loaded {}!", name);

				files.put(name, file);
			}
		} catch (IOException ex) {
			logger.error("Couldn't create nor load {}", file.getAbsolutePath(), ex);
		}
	}
	
	public String getString(String name, String path) {
		File file = files.get(name);
		
		if (file == null)
			return "";

		JSONParser parser = new JSONParser();
		try {
			
			Object obj = parser.parse(new FileReader(file));
			JSONObject jsonObject = (JSONObject)obj;
			Object res = null;
			
			for (String key : path.split("\\.")) {
				if (path.contains(".") && !key.equals(path.substring(path.lastIndexOf(".")+1, path.length())))
					jsonObject = (JSONObject) jsonObject.get(key);
				else {
					res = jsonObject.get(key);
					if (res.equals(null))
						throw new KeyIsNull();
				}
			}
			
			if (res.equals(null)) {
				logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
				return "";
			}
			
			return res.toString();
		
		} catch (KeyIsNull ex) {
			return "";
		} catch (IOException | ParseException ex) {
			logger.warn("Couldn't process file {}.json", path, name, ex);
			return "";
		}
	}
	
	public boolean getBoolean(String name, String path){
		File file = files.get(name);
		
		if(file == null)
			return false;
		
		JSONParser parser = new JSONParser();
		try {
			
			Object obj = parser.parse(new FileReader(file));
			JSONObject jsonObject = (JSONObject)obj;
			Object res = null;
			
			for (String key : path.split("\\.")) {
				if(res != null)
					break;
					   
				res = jsonObject.get(key);
			}
			
			if (res == null || res.equals(null))
				return false;
				
			return true;
		} catch (FileNotFoundException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
			return false;
		} catch (IOException | ParseException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
			return false;
		}
	}

	public List<String> getStringList(String name, String path){
		File file = files.get(name);
		
		if(file == null)
			return new ArrayList<>();

		JSONParser parser = new JSONParser();
		try {
			
			Object obj = parser.parse(new FileReader(file));
			JSONObject jsonObject = (JSONObject)obj;
			JSONArray jsonArray = null;
			
			for (String key : path.split("\\.")) {
				
				jsonArray = (JSONArray) jsonObject.get(key);
			}
			
			if (jsonArray == null || jsonArray.isEmpty())
				return new ArrayList<>();
				
			return Arrays.asList(Arrays.copyOf(jsonArray.toArray(), jsonArray.size(), String[].class));
		} catch (FileNotFoundException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
			return new ArrayList<>();
		} catch (IOException | ParseException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
			return new ArrayList<>();
		}
	}

	private boolean export(InputStream inputStream, String destination){
		boolean success = true;
		try {
			Files.copy(inputStream, Paths.get(destination), StandardCopyOption.REPLACE_EXISTING);
		} catch(IOException ex){
			success = false;
		}

		return success;
	}
}
