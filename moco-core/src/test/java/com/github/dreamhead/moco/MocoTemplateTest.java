package com.github.dreamhead.moco;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.github.dreamhead.moco.Moco.and;
import static com.github.dreamhead.moco.Moco.by;
import static com.github.dreamhead.moco.Moco.cookie;
import static com.github.dreamhead.moco.Moco.eq;
import static com.github.dreamhead.moco.Moco.file;
import static com.github.dreamhead.moco.Moco.header;
import static com.github.dreamhead.moco.Moco.jsonPath;
import static com.github.dreamhead.moco.Moco.path;
import static com.github.dreamhead.moco.Moco.pathResource;
import static com.github.dreamhead.moco.Moco.status;
import static com.github.dreamhead.moco.Moco.template;
import static com.github.dreamhead.moco.Moco.uri;
import static com.github.dreamhead.moco.Moco.var;
import static com.github.dreamhead.moco.Moco.version;
import static com.github.dreamhead.moco.Moco.xpath;
import static com.github.dreamhead.moco.Runner.running;
import static com.github.dreamhead.moco.helper.RemoteTestUtils.remoteUrl;
import static com.github.dreamhead.moco.helper.RemoteTestUtils.root;
import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.Assert.fail;

public class MocoTemplateTest extends AbstractMocoHttpTest {
    @Test
    public void should_generate_response_with_http_method() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.method}"));

        running(server, () -> {
            assertThat(helper.get(remoteUrl("/template")), is("GET"));
            assertThat(helper.postContent(remoteUrl("/template"), "foo"), is("POST"));
        });
    }

    @Test
    public void should_generate_response_with_http_version() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.version}"));

        running(server, () -> assertThat(helper.getWithVersion(remoteUrl("/template"), HttpVersion.HTTP_1_0), is("HTTP/1.0")));
    }

    @Test
    public void should_generate_response_with_content() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.content}"));

        running(server, () -> assertThat(helper.postContent(remoteUrl("/template"), "foo"), is("foo")));
    }

    @Test
    public void should_generate_response_with_http_header() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.headers['foo']}"));

        running(server, () -> assertThat(helper.getWithHeader(remoteUrl("/template"), ImmutableMultimap.of("foo", "bar")), is("bar")));
    }

    @Test(expected = HttpResponseException.class)
    public void should_throw_exception_for_unknown_header() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.headers['foo']}"));

        running(server, () -> helper.get(remoteUrl("/template")));
    }

    @Test
    public void should_generate_response_with_http_query() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.uri} ${req.queries['foo']}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/template?foo=bar"));
            assertThat(response, is("/template bar"));
        });
    }

    @Test
    public void should_generate_response_from_file() throws Exception {
        server.request(by(uri("/template"))).response(template(file("src/test/resources/foo.template")));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("GET")));
    }

    @Test
    public void should_generate_response_version() throws Exception {
        server.request(by(uri("/template"))).response(version(template("${req.version}")));

        running(server, () -> {
            ProtocolVersion version = helper.execute(Request.get(remoteUrl("/template"))
                    .version(HttpVersion.HTTP_1_0))
                    .getVersion();
            assertThat(version.toString(), is("HTTP/1.0"));
        });
    }

    @Test
    public void should_generate_response_header() throws Exception {
        server.request(by(uri("/template"))).response(header("foo", template("${req.method}")));

        running(server, () -> {
            Header header = helper.execute(Request.get(remoteUrl("/template")).version(HttpVersion.HTTP_1_0)).getFirstHeader("foo");
            assertThat(header.getValue(), is("GET"));
        });
    }

    @Test
    public void should_generate_response_with_uri() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.uri}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/template"));
            assertThat(response, is("/template"));
        });
    }

    @Test
    public void should_generate_response_with_form() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.forms['name']}"));

        running(server, () -> {
            Request request = Request.post(remoteUrl("/template")).bodyForm(new BasicNameValuePair("name", "dreamhead"));
            assertThat(helper.executeAsString(request), is("dreamhead"));
        });
    }

    @Test
    public void should_generate_response_with_cookie() throws Exception {
        server.request(and(by(uri("/cookie")), eq(cookie("templateLoggedIn"), "true"))).response(template("${req.cookies['templateLoggedIn']}"));
        server.request(by(uri("/cookie"))).response(cookie("templateLoggedIn", "true"), status(302));

        running(server, () -> {
            assertThat(helper.getForStatus(remoteUrl("/cookie")), is(302));
            assertThat(helper.get(remoteUrl("/cookie")), is("true"));
        });
    }

    @Test
    public void should_generate_response_with_variable() throws Exception {
        server.request(by(uri("/template"))).response(template("${var}", "var", "TEMPLATE"));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("TEMPLATE")));
    }

    @Test
    public void should_generate_response_with_two_variables() throws Exception {
        server.request(by(uri("/template"))).response(template("${foo} ${bar}", "foo", "ANOTHER", "bar", "TEMPLATE"));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("ANOTHER TEMPLATE")));
    }

    @Test
    public void should_generate_response_with_variable_map() throws Exception {
        server.request(by(uri("/template"))).response(template("${foo} ${bar}",
                of("foo", var("ANOTHER"), "bar", var("TEMPLATE"))));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("ANOTHER TEMPLATE")));
    }

    @Test
    public void should_generate_response_from_file_with_variable() throws Exception {
        server.request(by(uri("/template"))).response(template(file("src/test/resources/var.template"), "var", "TEMPLATE"));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("TEMPLATE")));
    }

    @Test
    public void should_generate_response_from_file_with_two_variables() throws Exception {
        server.request(by(uri("/template"))).response(template(file("src/test/resources/two_vars.template"),
                "foo", "ANOTHER", "bar", "TEMPLATE"));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("ANOTHER TEMPLATE")));
    }

//    @Test
//    public void should_generate_response_from_file_with_variable_map() throws Exception {
//        server.request(by(uri("/template"))).response(template(file("src/test/resources/var.template"), of("var", "TEMPLATE")));
//
//        running(server, new Runnable() {
//            @Override
//            public void run() throws Exception {
//                assertThat(helper.get(remoteUrl("/template")), is("TEMPLATE"));
//            }
//        });
//    }

    @Test
    public void should_generate_response_with_two_variables_by_request() throws Exception {
        server.request(by(uri("/template"))).response(template("${foo} ${bar}", "foo", jsonPath("$.book.price"), "bar", jsonPath("$.book.price")));

        running(server, () -> {
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"2\"}}"), is("2 2"));
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"1\"}}"), is("1 1"));
        });
    }

    @Test
    public void should_generate_response_with_variable_by_request() throws Exception {
        server.request(by(uri("/template"))).response(template("${foo}", "foo", jsonPath("$.book.price")));

        running(server, () -> {
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"2\"}}"), is("2"));
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"1\"}}"), is("1"));
        });
    }

    @Test
    public void should_generate_response_from_file_with_variable_by_request() throws Exception {
        server.request(by(uri("/template"))).response(template(file("src/test/resources/var.template"), "var", jsonPath("$.book.price")));

        running(server, () -> {
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"2\"}}"), is("2"));
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"1\"}}"), is("1"));
        });
    }

    @Test
    public void should_generate_response_from_file_with_two_variables_by_request() throws Exception {
        server.request(by(uri("/template"))).response(template(file("src/test/resources/two_vars.template"), "foo", jsonPath("$.book.price"), "bar", jsonPath("$.book.price")));

        running(server, () -> {
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"2\"}}"), is("2 2"));
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"1\"}}"), is("1 1"));
        });
    }

    @Test
    public void should_generate_response_with_two_variables_by_request_and_one_variable_is_plain_text() throws Exception {
        server.request(by(uri("/template"))).response(template("${foo} ${bar}", "foo", jsonPath("$.book.price"), "bar", var("bar")));

        running(server, () -> {
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"2\"}}"), is("2 bar"));
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"1\"}}"), is("1 bar"));
        });
    }

    @Test
    public void should_generate_response_from_file_with_variable_map() throws Exception {
        server.request(by(uri("/template"))).response(template(file("src/test/resources/var.template"), of("var", jsonPath("$.book.price"))));

        running(server, () -> {
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"2\"}}"), is("2"));
            assertThat(helper.postContent(remoteUrl("/template"), "{\"book\":{\"price\":\"1\"}}"), is("1"));
        });
    }

    @Test
    public void should_generate_response_with_many_extracted_variables() throws Exception {
        server.request(by(uri("/template"))).response(template("<#list seq as item>${item}</#list>", "seq", xpath("/request/parameters/id/text()")));

        running(server, () -> assertThat(helper.postFile(remoteUrl("/template"), "foobar.xml"), is("12")));
    }

    @Test
    public void should_return_file_with_template() throws Exception {
        server.request(by(uri("/template"))).response(file(template("src/test/resources/${var}", "var", "foo.response")));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("foo.response")));
    }

    @Test
    public void should_return_file_with_template_and_charset() throws Exception {
        server.request(by(uri("/template"))).response(file(template("src/test/resources/${var}", "var", "gbk.response"), Charset.forName("GBK")));

        running(server, () -> assertThat(helper.getAsBytes(remoteUrl("/template")), is(Files.readAllBytes(Paths.get("src/test/resources/gbk.response")))));
    }

    @Test
    public void should_return_path_resource_with_template() throws Exception {
        server.request(by(uri("/template"))).response(pathResource(template("${var}", "var", "foo.response")));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("foo.response")));
    }

    @Test
    public void should_return_path_resource_with_template_and_charset() throws Exception {
        server.request(by(uri("/template"))).response(pathResource(template("${var}", "var", "gbk.response"), Charset.forName("GBK")));

        running(server, () -> {
            URL resource = Resources.getResource("gbk.response");
            InputStream stream = resource.openStream();
            assertThat(helper.getAsBytes(remoteUrl("/template")), is(ByteStreams.toByteArray(stream)));
        });
    }

    @Test
    public void should_return_redirect_with_template() throws Exception {
        server.get(by(uri("/"))).response("foo");
        server.request(by(uri("/redirectTemplate"))).redirectTo(template("${var}", "var", root()));
        server.redirectTo(template("${var}", "var", root()));

        running(server, () -> {
            assertThat(helper.get(remoteUrl("/redirectTemplate")), is("foo"));
            assertThat(helper.get(remoteUrl("/anything")), is("foo"));
        });
    }

    @Test
    public void should_generate_response_with_now() throws Exception {
        server.request(by(uri("/template"))).response(template("${now('yyyy-MM-dd')}"));

        running(server, () -> {
            final ZonedDateTime now = ZonedDateTime.now();
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            assertThat(helper.get(remoteUrl("/template")), is(formatter.format(now)));
        });
    }

    @Test
    public void should_throw_exception_for_now_without_format() throws Exception {
        server.request(by(uri("/template"))).response(template("${now()}"));

        running(server, () -> {
            HttpResponse response = helper.getResponse(remoteUrl("/template"));
            assertThat(response.getCode(), is(400));
        });
    }

    @Test
    public void should_generate_response_with_random() throws Exception {
        server.request(by(uri("/random"))).response(template("${random()}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/random"));
            try {
                double result = Double.parseDouble(response);
                assertThat(result, greaterThanOrEqualTo(0d));
                assertThat(result, lessThanOrEqualTo(1d));
            } catch (NumberFormatException e) {
                fail();
            }
        });
    }

    @Test
    public void should_generate_response_with_random_with_range() throws Exception {
        server.request(by(uri("/random"))).response(template("${random(100)}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/random"));
            try {
                double result = Double.parseDouble(response);
                assertThat(result, lessThan(100d));
                assertThat(result, greaterThanOrEqualTo(0d));
            } catch (NumberFormatException e) {
                fail();
            }
        });
    }

    @Test
    public void should_generate_response_with_random_with_special_range() throws Exception {
        server.request(by(uri("/random"))).response(template("${random(99,100)}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/random"));
            try {
                double result = Double.parseDouble(response);
                assertThat(result, lessThanOrEqualTo(100d));
                assertThat(result, greaterThanOrEqualTo(99d));
            } catch (NumberFormatException e) {
                fail();
            }
        });
    }

    @Test
    public void should_generate_response_with_random_with_data_format() throws Exception {
        server.request(by(uri("/random"))).response(template("${random('###.######')}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/random"));
            try {
                String target = Iterables.get(Splitter.on('.').split(response), 1);
                assertThat(target.length(), lessThanOrEqualTo(6));

                double result = Double.parseDouble(response);
                assertThat(result, lessThanOrEqualTo(1d));
            } catch (NumberFormatException e) {
                fail();
            }
        });
    }

    @Test
    public void should_generate_response_with_random_with_range_and_data_format() throws Exception {
        server.request(by(uri("/random"))).response(template("${random(100, '###.######')}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/random"));
            try {
                double result = Double.parseDouble(response);
                assertThat(result, lessThanOrEqualTo(100d));
                assertThat(result, greaterThanOrEqualTo(0d));
                String target = Iterables.get(Splitter.on('.').split(response), 1);
                assertThat(target.length(), lessThanOrEqualTo(6));
            } catch (NumberFormatException e) {
                fail();
            }
        });
    }

    @Test
    public void should_generate_response_with_random_with_special_range_and_data_format() throws Exception {
        server.request(by(uri("/random"))).response(template("${random(99, 100, '###.######')}"));

        running(server, () -> {
            String response = helper.get(remoteUrl("/random"));
            try {
                double result = Double.parseDouble(response);
                assertThat(result, lessThan(100d));
                assertThat(result, greaterThanOrEqualTo(99d));
                String target = Iterables.get(Splitter.on('.').split(response), 1);
                assertThat(target.length(), lessThanOrEqualTo(6));
            } catch (NumberFormatException e) {
                fail();
            }
        });
    }

    @Test
    public void should_throw_exception_for_random_with_range_less_than_0() throws Exception {
        server.request(by(uri("/template"))).response(template("${random(-10)}"));

        running(server, () -> {
            HttpResponse response = helper.getResponse(remoteUrl("/template"));
            assertThat(response.getCode(), is(400));
        });
    }
    @Test
    public void should_throw_exception_for_random_with_start_greater_than_end() throws Exception {
        server.request(by(uri("/template"))).response(template("${random(100,99)}"));

        running(server, () -> {
            HttpResponse response = helper.getResponse(remoteUrl("/template"));
            assertThat(response.getCode(), is(400));
        });
    }
    @Test
    public void should_return_json() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.json.code} ${req.json.message}"));
        running(server, () -> assertThat(helper.postContent(remoteUrl("/template"), "{\"code\":1,\"message\":\"message\"}"), is("1 message")));
    }

    @Test
    public void should_throw_exception_for_unknown_json() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.json.code} ${req.json.message}"));
        running(server, () -> {
            HttpResponse response = helper.getResponse(remoteUrl("/template"));
            assertThat(response.getCode(), is(400));
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_throw_exception_for_reserved_name_as_variable_nem() {
        server.request(by(uri("/template"))).response(template("${random}", "random", "bar"));
    }

    @Test
    public void should_generate_response_with_dynamic_variable() throws Exception {
        server.request(by(uri("/template"))).response(template("${var}", "var", var((request) -> "TEMPLATE")));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("TEMPLATE")));
    }

    @Test
    public void should_generate_response_with_dynamic_function_variable() throws Exception {
        server.request(by(uri("/template"))).response(template("${var}", "var", var(request -> "TEMPLATE")));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("TEMPLATE")));
    }

    @Test
    public void should_return_with_xml() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.xml.parameter.id}"));
        running(server, () -> assertThat(helper.postContent(remoteUrl("/template"), "<request><parameter><id>1</id></parameter></request>"), is("1")));
    }

    @Test
    public void should_return_xml_with_declaration() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.xml.parameter.id}"));
        running(server, () -> assertThat(helper.postContent(remoteUrl("/template"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><parameter><id>1</id></parameter></request>"),
                is("1")));
    }

    @Test
    public void should_return_bad_request_for_unknown_xml() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.xml.parameter.id}"));
        running(server, () -> assertThat(helper.postForResponse(remoteUrl("/template"), "foo").getCode(), is(400)));
    }

    @Test
    public void should_generate_response_with_client_address() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.client.address}"));

        running(server, () -> assertThat(helper.get(remoteUrl("/template")), is("127.0.0.1")));
    }

    @Test
    public void should_generate_response_with_client_port() throws Exception {
        server.request(by(uri("/template"))).response(template("${req.client.port}"));

        running(server, () -> {
            final String actual = helper.get(remoteUrl("/template"));
            assertThat(actual, matchesRegex("^[1-9]\\d*$"));
        });
    }

    @Test
    public void should_generate_response_with_path() throws Exception {
        server.request(path(uri("/path/{path}"))).response(template("${req.path.path}"));
        server.request(path(uri("/path/{path}/sub/{sub}"))).response(template("${req.path.path}/${req.path.sub}"));

        running(server, () -> {
            assertThat(helper.get(remoteUrl("/path/hello")), is("hello"));
            assertThat(helper.get(remoteUrl("/path/foo")), is("foo"));
            assertThat(helper.get(remoteUrl("/path/foo/sub/bar")), is("foo/bar"));
        });
    }
}
