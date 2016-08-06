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

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.zain.blog.model.Common;
import cn.zain.blog.model.UserExt;
import cn.zain.blog.service.InitService;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Role;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.JSONRenderer;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Sessions;
import org.b3log.latke.util.Strings;
import cn.zain.blog.SoloServletListener;
import cn.zain.blog.processor.renderer.ConsoleRenderer;
import cn.zain.blog.processor.util.Filler;
import cn.zain.blog.util.QueryResults;
import cn.zain.blog.util.Thumbnails;
import org.json.JSONObject;

/**
 * Solo initialization service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.2.0.9, Apr 15, 2016
 * @since 0.4.0
 */
@RequestProcessor
public class InitProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(InitProcessor.class.getName());

    /**
     * Initialization service.
     */
    @Inject
    private InitService initService;

    /**
     * Filler.
     */
    @Inject
    private Filler filler;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Max user name length.
     */
    public static final int MAX_USER_NAME_LENGTH = 20;

    /**
     * Min user name length.
     */
    public static final int MIN_USER_NAME_LENGTH = 1;

    /**
     * Shows initialization page.
     *
     * @param context the specified http request context
     * @param request the specified http servlet request
     * @param response the specified http servlet response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/init", method = HTTPRequestMethod.GET)
    public void showInit(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        if (initService.isInited()) {
            response.sendRedirect("/");

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new ConsoleRenderer();

        renderer.setTemplateName("init.ftl");
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();

        final Map<String, String> langs = langPropsService.getAll(Locales.getLocale(request));

        dataModel.putAll(langs);

        dataModel.put(Common.VERSION, SoloServletListener.VERSION);
        dataModel.put(Common.STATIC_RESOURCE_VERSION, Latkes.getStaticResourceVersion());
        dataModel.put(Common.YEAR, String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));

        Keys.fillRuntime(dataModel);
        filler.fillMinified(dataModel);
    }

    /**
     * Initializes Solo.
     *
     * @param context the specified http request context
     * @param request the specified http servlet request, for example,      <pre>
     * {
     *     "userName": "",
     *     "userEmail": "",
     *     "userPassword": ""
     * }
     * </pre>
     *
     * @param response the specified http servlet response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/init", method = HTTPRequestMethod.POST)
    public void initSolo(final HTTPRequestContext context, final HttpServletRequest request,
            final HttpServletResponse response) throws Exception {
        if (initService.isInited()) {
            response.sendRedirect("/");

            return;
        }

        final JSONRenderer renderer = new JSONRenderer();

        context.setRenderer(renderer);

        final JSONObject ret = QueryResults.defaultResult();

        renderer.setJSONObject(ret);

        try {
            final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);

            final String userName = requestJSONObject.optString(User.USER_NAME);
            final String userEmail = requestJSONObject.optString(User.USER_EMAIL);
            final String userPassword = requestJSONObject.optString(User.USER_PASSWORD);

            if (Strings.isEmptyOrNull(userName) || Strings.isEmptyOrNull(userEmail) || Strings.isEmptyOrNull(userPassword)
                    || !Strings.isEmail(userEmail)) {
                ret.put(Keys.MSG, "Init failed, please check your input");

                return;
            }

            if (invalidUserName(userName)) {
                ret.put(Keys.MSG, "Init failed, please check your username (length [1, 20], content {a-z, A-Z, 0-9}, do not contain 'admin' for security reason]");

                return;
            }

            final Locale locale = Locales.getLocale(request);

            requestJSONObject.put(Keys.LOCALE, locale.toString());

            initService.init(requestJSONObject);

            // If initialized, login the admin
            final JSONObject admin = new JSONObject();
            admin.put(User.USER_NAME, userName);
            admin.put(User.USER_EMAIL, userEmail);
            admin.put(User.USER_ROLE, Role.ADMIN_ROLE);
            admin.put(User.USER_PASSWORD, userPassword);
            admin.put(UserExt.USER_AVATAR, Thumbnails.getGravatarURL(userEmail, "128"));

            Sessions.login(request, response, admin);

            ret.put(Keys.STATUS_CODE, true);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            ret.put(Keys.MSG, e.getMessage());
        }
    }

    /**
     * Checks whether the specified name is invalid.
     *
     * <p>
     * A valid user name:
     * <ul>
     * <li>length [1, 20]</li>
     * <li>content {a-z, A-Z, 0-9}</li>
     * <li>Not contains "admin"/"Admin"</li>
     * </ul>
     * </p>
     *
     * @param name the specified name
     * @return {@code true} if it is invalid, returns {@code false} otherwise
     */
    public static boolean invalidUserName(final String name) {
        final int length = name.length();
        if (length < MIN_USER_NAME_LENGTH || length > MAX_USER_NAME_LENGTH) {
            return true;
        }

        char c;
        for (int i = 0; i < length; i++) {
            c = name.charAt(i);

            if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '0' <= c && c <= '9') {
                continue;
            }

            return true;
        }

        return name.contains("admin") || name.contains("Admin");
    }
}
