package com.fexl.shulkerloader;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod("shulkerloader")
public class ShulkerLoader
{
	public static final String MOD_ID = "shulkerloader";
	
	public ShulkerLoader()
	{
		ShulkerLoad sloader = new ShulkerLoad();
		MinecraftForge.EVENT_BUS.register(sloader);
	}
}

