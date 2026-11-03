package com.seibel.distanthorizons.core.multiplayer.fullData;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class FullDataPayloadReceiver implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logNetworkEventToFile)
			.build();
	
	private final Cache<Integer, CompositeByteBuf> buffersById = CacheBuilder.newBuilder()
		.expireAfterAccess(30, TimeUnit.SECONDS)
			.<Integer, CompositeByteBuf>removalListener(notification -> notification.getValue().release())
			.<Integer, CompositeByteBuf>build();
	private boolean closed;
	
	@Override
	public synchronized void close()
	{
		this.closed = true;
		this.buffersById.invalidateAll();
	}
	
	public synchronized void receiveChunk(FullDataSplitMessage message)
	{
		if (this.closed)
		{
			message.buffer.release();
			return;
		}
		CompositeByteBuf composite = this.buffersById.getIfPresent(message.bufferId);
		if (message.isFirst)
		{
			composite = Unpooled.compositeBuffer();
			this.buffersById.put(message.bufferId, composite);
			LOGGER.debug("Created new full data buffer [" + message.bufferId + "]: [" + composite + "]");
		}
		else if (composite == null)
		{
			LOGGER.debug("Received non-first full data chunk for empty buffer [" + message.bufferId + "]: [" + message.buffer + "].");
			message.buffer.release();
			return;
		}

		// Do not replace the cache entry when appending: legacy Guava's Java 8
		// Map.compute default reports even the same value as a removed entry.
		message.buffer.readerIndex(0);
		composite.addComponent(message.buffer);
		composite.writerIndex(composite.writerIndex() + message.buffer.writerIndex());
		LOGGER.debug("Updated full data buffer [" + message.bufferId + "]: [" + composite + "].");
	}
	
	// Cache eviction and close must not release a buffer while it is being decoded.
	public synchronized FullDataSourceV2DTO decodeDataSource(FullDataPayload payload)
	{
		CompositeByteBuf compositeByteBuffer = this.buffersById.getIfPresent(payload.dtoBufferId);
		Objects.requireNonNull(compositeByteBuffer, "Unable to get a complete buffer for a received payload. Ignore this if it doesn't spam similar errors");
		
		FullDataSourceV2DTO dataSourceDto = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
		try
		{
			return INetworkObject.decodeToInstance(dataSourceDto, compositeByteBuffer);
		}
		catch (RuntimeException | Error e)
		{
			dataSourceDto.close();
			throw e;
		}
		finally
		{
			// Releasing the buffer is handled by cache
			this.buffersById.invalidate(payload.dtoBufferId);
		}
	}
	
}
