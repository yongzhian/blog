/*
 * Copyright (c) 2010-2016, b3log.org & hacpai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.zain.blog.util;

import java.util.Locale;

import org.b3log.latke.Latkes;
import org.testng.annotations.Test;

/**
 * {@link Thumbnails} test case.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Jul 29, 2013
 * @since 0.6.1
 */
public final class ThumbnailsTestCase {

    /**
     * Test method for {@linkplain Thumbnails#getGravatarURL(java.lang.String, java.lang.String)}.
     * 
     * @throws Exception exception
     */
    @Test
    public void getGravatarURL() throws Exception {
        Latkes.initRuntimeEnv();
        Latkes.setLocale(Locale.SIMPLIFIED_CHINESE);

        final String gravatarURL = Thumbnails.getGravatarURL("test@b3log.org", "128");

        System.out.println(gravatarURL);
    }
}
