package mtsofficialpack;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
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
 * that the folder/package this file is located in the loader will fail!  Also, don't forget to
 * change the package section up at the top when you re-name the folder this file is in.  
 * 
 * Should you be compiling this for your own use, any compiler from 1.10.2 onward should compile it with no issues.
 * 
 * Note that the "required-after" can also be used to require other mods besides MTS.
 * This is incredibly useful if your pack depends on another content pack, or a mod that's not MTS
 * (such as Immersive Engineering for steel, for example). 
 * 
 * @author don_bruce
 */
@Mod.EventBusSubscriber
@Mod(modid = MTSPackLoader.MODID, name = MTSPackLoader.MODNAME, version = MTSPackLoader.MODVER, dependencies = "required-after:mts@[11.0.0,12.0.0);", acceptedMinecraftVersions="[1.10.2,]")
public class MTSPackLoader{
	public static final String MODID="mtsofficialpack";
	public static final String MODNAME="The Official Vehicle Pack for MTS";
	public static final String MODVER="5.0.0";
	
	/**
	 * On class construction we look through this jar and send all pack vehicles
	 * and parts to the main mod.  We use reflection here to avoid the need to pack
	 * the core mod code when compiling this code.
	 */
	public MTSPackLoader(){
		try{
			Class packParserSystem = Class.forName("minecrafttransportsimulator.systems.PackParserSystem");
			Method addMultipartMethod = packParserSystem.getMethod("addMultipartDefinition", InputStreamReader.class, String.class, String.class);
			Method addPartMethod = packParserSystem.getMethod("addPartDefinition", InputStreamReader.class, String.class, String.class);
			
			String className = this.getClass().getSimpleName();
			String packageName = this.getClass().getPackage().getName();
			String classDir = this.getClass().getClassLoader().getResource(packageName).getPath();
			classDir = classDir.substring(0, classDir.indexOf('!')).replace("file:/", "");
			
			/*From here on out the system should search the jarfile this class is located in and find all the files
			In the assets/<MODID>/jsondefs/vehicles section.  It should take all files located in there and send
			them to the addMultipartMethod.  The same should happen for parts until all of them are loaded.
			The String parameter in both cases is the MODID, and should match the folder/package name here.
			Make sure the entries are sorted, however, as the parser can't do that itself.
			*/
			ZipFile jarFile = new ZipFile(classDir);
			Enumeration<? extends ZipEntry> entries = jarFile.entries();
			List<String> multipartEntries = new ArrayList<String>();
			List<String> partEntries = new ArrayList<String>();
			
			while(entries.hasMoreElements()){
				ZipEntry entry = entries.nextElement();		
				if(entry.getName().endsWith(".json")){					 
					if(entry.getName().contains("jsondefs/vehicles")){
						multipartEntries.add(entry.getName());
					}else if(entry.getName().contains("jsondefs/parts")){
						partEntries.add(entry.getName());
					}
				}
			}
			
			//Sort the the entries, and then send the off to be registered.
			multipartEntries.sort(null);
			for(String entryName : multipartEntries){
				String entryFileName = entryName.substring(entryName.lastIndexOf('/') + 1, entryName.length() - ".json".length());
				addMultipartMethod.invoke(null, new InputStreamReader(jarFile.getInputStream(jarFile.getEntry(entryName))), entryFileName, MODID);
			}
			partEntries.sort(null);
			for(String entryName : partEntries){
				String entryFileName = entryName.substring(entryName.lastIndexOf('/') + 1, entryName.length() - ".json".length());
				addPartMethod.invoke(null, new InputStreamReader(jarFile.getInputStream(jarFile.getEntry(entryName))), entryFileName, MODID);
			}
			jarFile.close();
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
	 */
	@SubscribeEvent
	public static void registerItems(RegistryEvent.Register<Item> event){
		try{
			Class registry = Class.forName("minecrafttransportsimulator.dataclasses.MTSRegistry");
			Method getItemsMethod = registry.getMethod("getItemsForPack", String.class);
			List<Item> itemList = (List<Item>) getItemsMethod.invoke(null, MODID);
			for(Item item : itemList){
				item.setRegistryName(new ResourceLocation(MODID, item.getUnlocalizedName().substring("item.".length())));
				event.getRegistry().register(item);
			}	
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/*
	 * 	This data needs to go in the propeller JSON files.
	@Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> subItems){
		if(CreativeTabCollection.tabMTSParts.equals(tab)){
			List<Byte[]> propellerList = new ArrayList<Byte[]>();
			propellerList.add(new Byte[]{0, 2, 70, 75});
			propellerList.add(new Byte[]{0, 3, 70, 75});
			propellerList.add(new Byte[]{0, 4, 70, 75});
			propellerList.add(new Byte[]{1, 2, 70, 75});
			propellerList.add(new Byte[]{1, 3, 70, 75});
			propellerList.add(new Byte[]{1, 4, 70, 75});
			propellerList.add(new Byte[]{1, 2, 70, 115});
			propellerList.add(new Byte[]{2, 2, 70, 75});
			propellerList.add(new Byte[]{2, 3, 70, 75});
			propellerList.add(new Byte[]{2, 4, 70, 75});
			propellerList.add(new Byte[]{2, 2, 70, 115});
			propellerList.add(new Byte[]{2, 3, 70, 115});
			propellerList.add(new Byte[]{2, 4, 70, 115});
			
			for(Byte[] propellerProperties : propellerList){
				ItemStack propellerStack = new ItemStack(MTSRegistry.propeller, 1, propellerProperties[0]);
				NBTTagCompound stackTag = new NBTTagCompound();
				stackTag.setByte("type", propellerProperties[0]);
				stackTag.setInteger("numberBlades", propellerProperties[1]);
				stackTag.setInteger("pitch", propellerProperties[2]);
				stackTag.setInteger("diameter", propellerProperties[3]);
				if(propellerProperties[0]==1){
					stackTag.setFloat("health", 500);
				}else if(propellerProperties[0]==2){
					stackTag.setFloat("health", 1000);
				}else{
					stackTag.setFloat("health", 100);
				}
				propellerStack.setTagCompound(stackTag);
				subItems.add(propellerStack);
			}
		}
    }
	 * 
	 */
}
