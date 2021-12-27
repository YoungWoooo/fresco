/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.imagepipeline.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.facebook.common.memory.PooledByteBuffer;
import com.facebook.common.memory.PooledByteBufferInputStream;
import com.facebook.common.references.CloseableReference;
import com.facebook.imagepipeline.testing.FakeAshmemMemoryChunk;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Basic tests for {@link MemoryPooledByteBuffer} */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MemoryPooledByteBufferTest {
  private static final byte[] BYTES = new byte[] {1, 4, 5, 0, 100, 34, 0, 1, -1, -1};
  private static final int BUFFER_LENGTH = BYTES.length - 2;

  @Mock private AshmemMemoryChunkPool mAshmemPool;
  private AshmemMemoryChunk mAshmemChunk;
  private MemoryPooledByteBuffer mBufferPooledByteBuffer;

  @Before
  public void setUp() {
    mAshmemChunk = new FakeAshmemMemoryChunk(BYTES.length);
    mAshmemChunk.write(0, BYTES, 0, BYTES.length);
    mAshmemPool = mock(AshmemMemoryChunkPool.class);
    CloseableReference<MemoryChunk> bufferPoolRef =
        CloseableReference.of(mAshmemChunk, mAshmemPool);
    mBufferPooledByteBuffer = new MemoryPooledByteBuffer(bufferPoolRef, BUFFER_LENGTH);
    bufferPoolRef.close();
  }

  @Test
  public void testBasic() {
    testBasic(mBufferPooledByteBuffer, mAshmemChunk);
  }

  @Test
  public void testSimpleRead() {
    testSimpleRead(mBufferPooledByteBuffer);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSimpleReadOutOfBoundsUsingBufferPool() {
    mBufferPooledByteBuffer.read(BUFFER_LENGTH);
  }

  @Test
  public void testRangeRead() {
    testRangeRead(mBufferPooledByteBuffer);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRangeReadOutOfBoundsUsingBufferPool() {
    testRangeReadOutOfBounds(mBufferPooledByteBuffer);
  }

  @Test
  public void testReadFromStream() throws Exception {
    testReadFromStream(mBufferPooledByteBuffer);
  }

  @Test
  public void testClose() {
    testClose(mBufferPooledByteBuffer, mAshmemChunk, mAshmemPool);
  }

  @Test(expected = PooledByteBuffer.ClosedException.class)
  public void testGettingSizeAfterCloseUsingBufferPool() {
    mBufferPooledByteBuffer.close();
    mBufferPooledByteBuffer.size();
  }

  private static void testBasic(
      final MemoryPooledByteBuffer mPooledByteBuffer, final MemoryChunk mChunk) {
    assertFalse(mPooledByteBuffer.isClosed());
    assertSame(mChunk, mPooledByteBuffer.getCloseableReference().get());
    assertEquals(BUFFER_LENGTH, mPooledByteBuffer.size());
  }

  private static void testSimpleRead(final MemoryPooledByteBuffer mPooledByteBuffer) {
    for (int i = 0; i < 100; ++i) {
      final int offset = i % BUFFER_LENGTH;
      assertEquals(BYTES[offset], mPooledByteBuffer.read(offset));
    }
  }

  private static void testRangeRead(final MemoryPooledByteBuffer mPooledByteBuffer) {
    byte[] readBuf = new byte[BUFFER_LENGTH];
    mPooledByteBuffer.read(1, readBuf, 1, BUFFER_LENGTH - 2);
    assertEquals(0, readBuf[0]);
    assertEquals(0, readBuf[BUFFER_LENGTH - 1]);
    for (int i = 1; i < BUFFER_LENGTH - 1; ++i) {
      assertEquals(BYTES[i], readBuf[i]);
    }
  }

  private static void testRangeReadOutOfBounds(final MemoryPooledByteBuffer mPooledByteBuffer) {
    byte[] readBuf = new byte[BUFFER_LENGTH];
    mPooledByteBuffer.read(1, readBuf, 0, BUFFER_LENGTH);
  }

  private static void testReadFromStream(final MemoryPooledByteBuffer mPooledByteBuffer)
      throws Exception {
    InputStream is = new PooledByteBufferInputStream(mPooledByteBuffer);
    byte[] tmp = new byte[BUFFER_LENGTH + 1];
    int bytesRead = is.read(tmp, 0, tmp.length);
    assertEquals(BUFFER_LENGTH, bytesRead);
    for (int i = 0; i < BUFFER_LENGTH; i++) {
      assertEquals(BYTES[i], tmp[i]);
    }
    assertEquals(-1, is.read());
  }

  private static void testClose(
      final MemoryPooledByteBuffer mPooledByteBuffer,
      final MemoryChunk mChunk,
      final MemoryChunkPool mPool) {
    mPooledByteBuffer.close();
    assertTrue(mPooledByteBuffer.isClosed());
    assertNull(mPooledByteBuffer.getCloseableReference());
    verify(mPool).release(mChunk);
  }
}
