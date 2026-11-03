package tests;

import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadReceiver;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FullDataPayloadReceiverTest
{
	@Test
	public void successfulDecodingReleasesThePayload() throws Exception
	{
		try (FullDataPayloadReceiver receiver = new FullDataPayloadReceiver();
			FullDataSourceV2DTO source = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding())
		{
			source.pos = 123456;
			ByteBuf bytes = Unpooled.buffer();
			source.encode(bytes);
			ByteBuf first = bytes.copy(0, 9);
			ByteBuf second = bytes.copy(9, bytes.writerIndex() - 9);
			bytes.release();
			receiver.receiveChunk(new FullDataSplitMessage(7, first, true));
			receiver.receiveChunk(new FullDataSplitMessage(7, second, false));
			assertEquals(1, first.refCnt());
			assertEquals(1, second.refCnt());
			FullDataPayload payload = new FullDataPayload();
			payload.dtoBufferId = 7;
			try (FullDataSourceV2DTO decoded = receiver.decodeDataSource(payload))
			{
				assertEquals(source.pos, decoded.pos);
			}
			assertEquals(0, first.refCnt());
			assertEquals(0, second.refCnt());
		}
	}

	@Test
	public void closeReleasesAllOwnedChunksAndRejectsLateChunks()
	{
		FullDataPayloadReceiver receiver = new FullDataPayloadReceiver();
		ByteBuf first = Unpooled.buffer().writeByte(1);
		ByteBuf second = Unpooled.buffer().writeByte(2);
		receiver.receiveChunk(new FullDataSplitMessage(7, first, true));
		receiver.receiveChunk(new FullDataSplitMessage(7, second, false));
		receiver.close();
		receiver.close();
		assertEquals(0, first.refCnt());
		assertEquals(0, second.refCnt());
		ByteBuf late = Unpooled.buffer().writeByte(3);
		receiver.receiveChunk(new FullDataSplitMessage(8, late, true));
		assertEquals(0, late.refCnt());
	}

	@Test
	public void orphanAndReplacedChunksAreReleased()
	{
		try (FullDataPayloadReceiver receiver = new FullDataPayloadReceiver())
		{
			ByteBuf orphan = Unpooled.buffer().writeByte(1);
			receiver.receiveChunk(new FullDataSplitMessage(7, orphan, false));
			assertEquals(0, orphan.refCnt());
			ByteBuf old = Unpooled.buffer().writeByte(2);
			receiver.receiveChunk(new FullDataSplitMessage(7, old, true));
			receiver.receiveChunk(new FullDataSplitMessage(7, Unpooled.buffer().writeByte(3), true));
			assertEquals(0, old.refCnt());
		}
	}

	@Test
	public void failedDecodingStillReleasesThePayload()
	{
		try (FullDataPayloadReceiver receiver = new FullDataPayloadReceiver())
		{
			ByteBuf truncated = Unpooled.buffer().writeByte(1);
			receiver.receiveChunk(new FullDataSplitMessage(7, truncated, true));
			FullDataPayload payload = new FullDataPayload();
			payload.dtoBufferId = 7;
			try
			{
				receiver.decodeDataSource(payload);
				fail("A truncated payload must fail decoding");
			}
			catch (IndexOutOfBoundsException expected) { }
			assertEquals(0, truncated.refCnt());
		}
	}
}
