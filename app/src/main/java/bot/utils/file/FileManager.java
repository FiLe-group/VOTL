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
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import bot.App;
import bot.constants.Constants;

class KeyIsNull extends Exception {
	public KeyIsNull(String str) {
		super(str);
	}
}

public class FileManager {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(FileManager.class);
	
	private Map<String, File> files;
	private List<String> languages;

	public FileManager() {
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
		// 
		String[] split = external.contains("/") ? external.split(File.separator) : external.split(Pattern.quote(File.separator));

		try {
			if (!file.exists()) {
				if ((split.length == 2 && !split[0].equals(".")) || (split.length >= 3 && split[0].equals("."))) {
					if (!file.getParentFile().mkdirs() && !file.getParentFile().exists()) {
						logger.warn("Failed to create directory {}", split[1]);
						return;
					}
				}
				if (file.createNewFile()) {
					if (export(App.class.getResourceAsStream(internal.replace(File.separator, "/")), external)) {
						logger.info("Successfully created {}!", name);
						files.put(name, file);
					} else {
						logger.warn("Failed to create {}!", name);
					}
				}
			} else {
				logger.info("Successfully located {}!", name);
				files.put(name, file);
			}
		} catch (IOException ex) {
			logger.error("Couldn't locate nor create {}", file.getAbsolutePath(), ex);
		}
	}
	
	@Nonnull
	public String getString(String name, String path) {
		File file = files.get(name);

		JSONParser parser = new JSONParser();
		String text = "";
		try {
			if (file == null)
				throw new FileNotFoundException();
			
			Object obj = parser.parse(new FileReader(file));
			JSONObject jsonObject = (JSONObject)obj;
			Object res = null;
			
			for (String key : path.split("\\.")) {
				if (path.contains(".") && !key.equals(path.substring(path.lastIndexOf(".")+1, path.length())))
					jsonObject = (JSONObject) jsonObject.get(key);
				else {
					try {
						res = jsonObject.get(key);
					} catch (NullPointerException ex) {
						throw new KeyIsNull("ERROR at file manager");
					}						
				}
			}

			if (res == null)
				throw new KeyIsNull(path);
			
			text = String.valueOf(res);
		
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}", name);
			text = "ERROR: file not found";
		} catch (KeyIsNull ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
			text = ex.getMessage();
		} catch (IOException | ParseException ex) {
			logger.warn("Couldn't process file {}.json", name, ex);
			text = "ERROR at processing file";
		}

		return Objects.requireNonNull(text);
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
		} catch (FileNotFoundException | ParseException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
			return false;
		} catch (IOException ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name, ex);
			return false;
		}
	}

	@Nonnull
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
				
			return Objects.requireNonNull(Arrays.asList(Arrays.copyOf(jsonArray.toArray(), jsonArray.size(), String[].class)));
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
		} catch (IOException | NullPointerException ex){
			success = false;
		}

		return success;
	}
}
