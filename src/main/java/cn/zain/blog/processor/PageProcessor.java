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
package cn.zain.blog.processor;


import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.zain.blog.model.Common;
import cn.zain.blog.model.Option;
import cn.zain.blog.service.CommentQueryService;
import cn.zain.blog.service.PreferenceQueryService;
import cn.zain.blog.util.Markdowns;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.servlet.renderer.freemarker.FreeMarkerRenderer;
import org.b3log.latke.util.Stopwatchs;
import cn.zain.blog.model.Page;
import cn.zain.blog.processor.util.Filler;
import cn.zain.blog.service.StatisticMgmtService;
import cn.zain.blog.util.Skins;
import org.json.JSONObject;


/**
 * Page processor.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.0.5, Nov 20, 2015
 * @since 0.3.1
 */
@RequestProcessor
public class PageProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(PageProcessor.class.getName());

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Filler.
     */
    @Inject
    private Filler filler;

    /**
     * Preference query service.
     */
    @Inject
    private PreferenceQueryService preferenceQueryService;

    /**
     * Comment query service.
     */
    @Inject
    private CommentQueryService commentQueryService;

    /**
     * Statistic management service.
     */
    @Inject
    private StatisticMgmtService statisticMgmtService;

    /**
     * Shows page with the specified context.
     *
     * @param context the specified context
     */
    @RequestProcessing(value = "/page", method = HTTPRequestMethod.GET)
    public void showPage(final HTTPRequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new FreeMarkerRenderer();

        context.setRenderer(renderer);

        renderer.setTemplateName("page.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final HttpServletRequest request = context.getRequest();
        final HttpServletResponse response = context.getResponse();

        try {
            final JSONObject preference = preferenceQueryService.getPreference();

            if (null == preference) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            Skins.fillLangs(preference.getString(Option.ID_C_LOCALE_STRING), (String) request.getAttribute(Keys.TEMAPLTE_DIR_NAME), dataModel);

            final Map<String, String> langs = langPropsService.getAll(Latkes.getLocale());

            // See PermalinkFilter#dispatchToArticleOrPageProcessor()
            final JSONObject page = (JSONObject) request.getAttribute(Page.PAGE);

            if (null == page) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            final String pageId = page.getString(Keys.OBJECT_ID);

            page.put(Common.COMMENTABLE, preference.getBoolean(Option.ID_C_COMMENTABLE) && page.getBoolean(Page.PAGE_COMMENTABLE));
            page.put(Common.PERMALINK, page.getString(Page.PAGE_PERMALINK));
            dataModel.put(Page.PAGE, page);
            final List<JSONObject> comments = commentQueryService.getComments(pageId);

            dataModel.put(Page.PAGE_COMMENTS_REF, comments);

            // Markdown
            if ("CodeMirror-Markdown".equals(page.optString(Page.PAGE_EDITOR_TYPE))) {
                Stopwatchs.start("Markdown Page[id=" + page.optString(Keys.OBJECT_ID) + "]");

                final String content = page.optString(Page.PAGE_CONTENT);

                page.put(Page.PAGE_CONTENT, Markdowns.toHTML(content));

                Stopwatchs.end();
            }

            filler.fillSide(request, dataModel, preference);
            filler.fillBlogHeader(request, response, dataModel, preference);
            filler.fillBlogFooter(request, dataModel, preference);

            statisticMgmtService.incBlogViewCount(request, response);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (final IOException ex) {
                LOGGER.error(ex.getMessage());
            }
        }
    }
}
