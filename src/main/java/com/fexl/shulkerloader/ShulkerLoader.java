package com.fexl.shulkerloader;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ShulkerLoader.MODID, name = ShulkerLoader.NAME, version = ShulkerLoader.VERSION)
public class ShulkerLoader
{
	public static final String MODID = "shulkerloader";
    public static final String NAME = "Shulker Loader";
    public static final String VERSION = "1.0.2";
    public static final String ACCEPTED_VERSIONS = "[1.12.2]";
	
	@Instance
	public ShulkerLoader instance;
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		ShulkerLoad sloader = new ShulkerLoad();
		MinecraftForge.EVENT_BUS.register(sloader);
	}
}

