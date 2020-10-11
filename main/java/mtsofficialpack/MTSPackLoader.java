package mtsofficialpack;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * This mod is a generic class that will interact with MTS and do all required pack loading things.
 * The only thing that needs to be changed in order to adapt it for other packs are the static
 * strings below, the mcmod.info file, and the package/file name this class, and the assets, 
 * are located in. Note that ALL of those strings should be the same.  If your MODID is different
 * than the folder/package this file is located in the loader will fail!  Also, don't forget to
 * change the package section up at the top when you re-name the folder this file is in.  
 * 
 * Should you be compiling this for your own use, any compiler from 1.10.2 onward should do so with no issues.
 * 
 * Note that the "required-after" can also be used to require other mods besides MTS.
 * This is useful if your pack depends on another content pack, or a mod that's not MTS
 * (such as Immersive Engineering for steel, for example). 
 * 
 * @author don_bruce
 */
@Mod.EventBusSubscriber
//DO NOT MODIFY THIS LINE!  Modify the things below this line instead.  Modifying this line will cause your pack to crash.
@Mod(modid=MTSPackLoader.MODID, name=MTSPackLoader.MODNAME, version=MTSPackLoader.MODVER, dependencies=MTSPackLoader.DEPS, acceptedMinecraftVersions=MTSPackLoader.MCVERS)
public class MTSPackLoader{
	//The ID for your pack.  Must be unique to every mod and pack in the world!
	public static final String MODID="mtsofficialpack";
	//Short title for your pack.
	public static final String MODNAME="The Official Vehicle Pack for MTS";
	//Pack version.  May be linked to from other packs to force specific versions.  Otherwise unused.
	public static final String MODVER="17.0.0";
	//Mods/packs and the versions needed to run this pack.  Put whatever you want, but always leave MTS in here to ensure players have it loaded.
	public static final String DEPS="required-after:mts@[19.0.0,);";
	//What MC versions this pack supports.  If you're using an older MTSPackLoader file and older JSON you can support 1.10.2-1.12.2.
	public static final String MCVERS="[1.12.2,]";
	
	/**
	 * On class construction we look through this jar and send all pack vehicles
	 * and parts to the main mod.  We use reflection here to avoid the need to pack
	 * the core mod code when compiling this code.
	 */
	public MTSPackLoader(){
		try{
			//Get the current class dir.
			String className = this.getClass().getSimpleName();
			String packageName = this.getClass().getPackage().getName();
			String classDir = this.getClass().getClassLoader().getResource(packageName).getPath();
			//Need this to remove % escape characters like %20 for spaces.
			URI classURI = new URI(classDir);
			classDir = classURI.getPath();
			classDir = classDir.substring(0, classDir.indexOf('!'));
			
			//Get the instance of the PackParserSystem.
			Class packParserSystem = Class.forName("minecrafttransportsimulator.systems.PackParserSystem");
			
			//Get the method that has all the current pack names and get those names.
			Method getPackContentNamesMethod = packParserSystem.getMethod("getValidPackContentNames");
			String[] contentNames = (String[]) getPackContentNamesMethod.invoke(null);
			
			//Iterate through all the content names, adding them as applicable.
			for(String contentName : contentNames){
				//Use a list to save the names so we can sort them so they appear in order in the Creative Tabs.
				List<String> entryNames = new ArrayList<String>();
				
				//Capitalize the first letter of the content name and use it to get the appropriate method.
				Method addContentMethod = packParserSystem.getMethod("add" + contentName.substring(0, 1).toUpperCase() + contentName.substring(1) + "Definition", InputStreamReader.class, String.class, String.class);
				
				//Search the jarfile this class is located in and find all the files in the assets/<MODID>/jsondefs/<contentName> section.
				ZipFile jarFile = new ZipFile(classDir);
				Enumeration<? extends ZipEntry> entries = jarFile.entries();
				while(entries.hasMoreElements()){
					ZipEntry entry = entries.nextElement();
					if(entry.getName().endsWith(".json") && entry.getName().contains("jsondefs/" + contentName + "s")){
						entryNames.add(entry.getName());
					}
				}
				
				//Sort the list and add send all items to MTS.
				entryNames.sort(null);
				for(String entryName : entryNames){
					String entryFileName = entryName.substring(entryName.lastIndexOf('/') + 1, entryName.length() - ".json".length());
					addContentMethod.invoke(null, new InputStreamReader(jarFile.getInputStream(jarFile.getEntry(entryName)), "UTF-8"), entryFileName, MODID);
				}
				jarFile.close();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Normally the core mod would handle everything, but since Forge
	 * doesn't like us registering items that aren't in our own mods
	 * we need to register them here when prompted.  The unlocalized name
	 * of the items will have been set prior by the core mod, so use that
	 * and the modID here to determine the registry name.  Yes, it's poor form, 
	 * but Forge needs to lay off the restrictions on registration names.
	 * 
	 * We also need to set the unlocalized name again, this time with our MODID as a prefix.
	 * If we don't it's very likely we will conflict with other packs with respect to entries
	 * in the .lang language files.
	 */
	@SubscribeEvent
	public static void registerItems(RegistryEvent.Register<Item> event){
		try{
			//First get the MTS classes.
			Class registry = Class.forName("minecrafttransportsimulator.dataclasses.MTSRegistry");
			Method getItemsMethod = registry.getMethod("getItemsForPack", String.class);
			List<Item> itemList = (List<Item>) getItemsMethod.invoke(null, MODID);
			
			//Now register the pack items.  Use the unlocalized name as it's the only thing we can set MTS-side 
			//that packs can see.  The name will be in the format of item.modid.name, so make sure to prune the 
			//item.modid. portion to get a reasonable registry name.
			for(Item item : itemList){
				item.setRegistryName(new ResourceLocation(MODID, item.getUnlocalizedName().replace("item." + MODID + ".", "")));
				event.getRegistry().register(item);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}