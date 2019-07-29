package com.bumptech.glide.load.resource.file;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.util.Preconditions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class FileDecoderTest {

    private FileDecoder decoder;
    private Options options;

    @Before
    public void setUp() {
        decoder = new FileDecoder();
        options = new Options();
    }

    @Test
    public void testReturnsGivenFileAsResource() throws IOException {
        File expected = new File("testFile");
        Resource<File> decoded = Preconditions.checkNotNull(decoder.decode(expected, 1, 1, options));

        assertEquals(expected, decoded.get());
    }
}
