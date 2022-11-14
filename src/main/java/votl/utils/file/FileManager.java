package votl.utils.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import votl.App;
import votl.objects.constants.Constants;

import net.dv8tion.jda.api.interactions.DiscordLocale;

import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import ch.qos.logback.classic.Logger;

class KeyIsNull extends Exception {
	public KeyIsNull(String str) {
		super(str);
	}
}

public class FileManager {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(FileManager.class);
	
	private Map<String, File> files;
	private List<DiscordLocale> locales;

	public FileManager() {
	}

	public FileManager addFile(String name, String internal, String external){
		createOrLoad(name, internal, external);
		
		return this;
	}
	
	// Convenience method do add new languages more easy.
	public FileManager addLang(@Nonnull String file) throws Exception {
		if (locales == null)
		locales = new ArrayList<>();
		
		DiscordLocale locale = DiscordLocale.from(file);
		if (locale.equals(DiscordLocale.UNKNOWN)) {
			throw new Exception("Unknown language was provided");
		}
		locales.add(locale);

		return addFile(file, Constants.LANG_DIR + file + ".json", Constants.DATA_PATH + Constants.LANG_DIR + file + ".json");
	}
	
	public Map<String, File> getFiles() {
		return files;
	}
	
	public List<DiscordLocale> getLanguages() {
		return locales;
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

		String text = "";
		try {
			if (file == null)
				throw new FileNotFoundException();

			Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

			text = JsonPath.using(conf).parse(file).read("$." + path);

			if (text == null || text.isEmpty())
				throw new KeyIsNull(path);
		
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
			text = "TEXT ERROR: file not found";
		} catch (KeyIsNull ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
			text = ex.getMessage();
		} catch (IOException ex) {
			logger.error("Couldn't process file {}.json", name, ex);
			text = "ERROR at processing file";
		}

		return Objects.requireNonNull(text);
	}
	
	public boolean getBoolean(String name, String path){
		File file = files.get(name);
		
		if(file == null)
			return false;
		
		try {
			
			Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

			Object res = JsonPath.using(conf).parse(file).read("$." + path);
			
			if (res == null || res.equals(null))
				return false;
				
			return true;
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
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

		List<String> array = new ArrayList<String>();
		try {
			
			Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);

			array = JsonPath.using(conf).parse(file).read("$." + path);	
			
			if (array == null || array.isEmpty())
				throw new KeyIsNull(path);
				
			return array;
		} catch (FileNotFoundException ex) {
			logger.error("Couldn't find file {}.json", name);
			return new ArrayList<>();
		} catch (KeyIsNull ex) {
			logger.warn("Couldn't find \"{}\" in file {}.json", path, name);
			return new ArrayList<>();
		} catch (IOException ex) {
			logger.warn("Couldn't process file {}.json", name, ex);
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
