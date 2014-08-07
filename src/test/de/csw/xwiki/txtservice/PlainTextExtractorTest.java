/*******************************************************************************
 * This file is part of the Coporate Semantic Web Project.
 * 
 * This work has been partially supported by the ``InnoProfile-Corporate Semantic Web" project funded by the German Federal
 * Ministry of Education and Research (BMBF) and the BMBF Innovation Initiative for the New German Laender - Entrepreneurial Regions.
 * 
 * http://www.corporate-semantic-web.de/
 * 
 * Freie Universitaet Berlin
 * Copyright (c) 2007-2013
 * 
 * Institut fuer Informatik
 * Working Group Coporate Semantic Web
 * Koenigin-Luise-Strasse 24-26
 * 14195 Berlin
 * 
 * http://www.mi.fu-berlin.de/en/inf/groups/ag-csw/
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package de.csw.xwiki.txtservice;

import java.io.IOException;
import java.util.TreeMap;

import org.junit.Assert;
import org.testng.annotations.Test;

public class PlainTextExtractorTest {

    // private static final Logger log = Logger.getLogger(PlainTextExtractorTest.class);

    /**
     * Test simple plaintext extraction.
     * ToDo: use text/offset format file to generate test files instead of bunch strings 
     */
    @Test
    public void extractPlainText() {

        XWikiTextServiceEnhancer xwt = new XWikiTextServiceEnhancer();
        TreeMap<Integer, Integer> offsets = new TreeMap<>();

        //                       1         2         3         4         5         6
        //             0123456789012345678901234567890123456789012345678901234567890123456789
        String text = "Some [[Text>>doc:link]] and macro {{code}}code{{/code}} text.";
        String plainText = xwt.extractPlainText(text, offsets);

        // XXX: here maybe we want the link label, but do not get it now
        //                     012345678901234567890123
        String expectedText = "Some  and macro  text.";
        Assert.assertEquals(expectedText, plainText);

        for (int i = 0; i < plainText.length(); i++) {
            int offset = xwt.getRealIndex(offsets, i);
            Assert.assertEquals("at position " + i + " with prefix [[" + plainText.substring(0, i) + "]] : with offset " + offset,
                    String.valueOf(plainText.charAt(i)), String.valueOf(text.charAt(offset)));
        }

        TreeMap<Integer, Integer> expectedOffsets = new TreeMap<>();
        expectedOffsets.put(5, 23 - 5);
        expectedOffsets.put(16, 55 - 16);
        Assert.assertEquals(expectedOffsets, offsets);

    }

    /**
     * @throws IOException
     */
    @Test
    public void extractMorePlainText() throws IOException {
        assertPlainText("", "");

        assertPlainText("Text  and ", "Text [[link>>target]] and [[rest]]");
        assertPlainText("A  followed by ", "A {{html}}macro{{/html}} followed by [[link]]");
        assertPlainText("B  followed by  and  text.", "B {{html}}macro{{/html}} followed by [[link]] and {{html}}more{{/html}} text.");
        assertPlainText("C  followed by  and  text, finishing with a ",
                "C {{html}}macro{{/html}} followed by [[link]] and {{html}}more{{/html}} text, finishing with a [[link>>doc:test]]");

        assertPlainText("Two ", "Two [[consecutive>>target]][[links]]");
        assertPlainText("Two  with more text", "Two [[consecutive>>target]][[links]] with more text");

        assertPlainText("", "{{include document=\"Panels.PanelSheet\"/}}");

    }

    private void assertPlainText(String expectedPlainText, String text) {
        XWikiTextServiceEnhancer xwt = new XWikiTextServiceEnhancer();
        TreeMap<Integer, Integer> offsets = new TreeMap<>();
        String plainText = xwt.extractPlainText(text, offsets);

        Assert.assertEquals(expectedPlainText, plainText);

        for (int i = 0; i < plainText.length(); i++) {
            int offset = xwt.getRealIndex(offsets, i);
            Assert.assertEquals(
                    "at position " + i + " with prefix [[" + plainText.substring(0, i) + "]] : with offset " + offset + " (set is " + offsets + ")",
                    String.valueOf(plainText.charAt(i)), String.valueOf(text.charAt(offset)));
        }

    }

}