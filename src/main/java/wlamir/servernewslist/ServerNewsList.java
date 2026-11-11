package wlamir.servernewslist;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wlamir.servernewslist.network.NewsRequestPayload;
import wlamir.servernewslist.network.NewsResponsePayload;
import wlamir.servernewslist.network.UpdateCheckPayload;
import wlamir.servernewslist.server.ServerNewsListServer;

public class ServerNewsList implements ModInitializer {
	public static final String MOD_ID = "servernewslist";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("✨ ServerNewsList is here! ✨");



		PayloadTypeRegistry.serverboundPlay().register(NewsRequestPayload.TYPE, NewsRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				NewsResponsePayload.TYPE,
				NewsResponsePayload.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(UpdateCheckPayload.TYPE, UpdateCheckPayload.CODEC);

		ServerNewsListServer.register();



		LOGGER.info("📸 ServerNewsList is initialized! 📸");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
