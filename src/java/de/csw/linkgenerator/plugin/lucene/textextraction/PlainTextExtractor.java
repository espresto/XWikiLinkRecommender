package de.csw.linkgenerator.plugin.lucene.textextraction;

import de.csw.linkgenerator.plugin.lucene.textextraction.MimetypeTextExtractor;

public class PlainTextExtractor implements MimetypeTextExtractor
{
    public String getText(byte[] data) throws Exception
    {
        return new String(data);
    }
}
