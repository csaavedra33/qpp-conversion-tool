package gov.cms.qpp.conversion;

import gov.cms.qpp.TestHelper;
import gov.cms.qpp.conversion.encode.EncodeException;
import gov.cms.qpp.conversion.encode.JsonWrapper;
import gov.cms.qpp.conversion.encode.QppOutputEncoder;
import gov.cms.qpp.conversion.model.ComponentKey;
import gov.cms.qpp.conversion.model.Program;
import gov.cms.qpp.conversion.model.TemplateId;
import gov.cms.qpp.conversion.model.error.AllErrors;
import gov.cms.qpp.conversion.model.error.Detail;
import gov.cms.qpp.conversion.model.error.Error;
import gov.cms.qpp.conversion.model.error.TransformException;
import gov.cms.qpp.conversion.stubs.Jenncoder;
import gov.cms.qpp.conversion.stubs.JennyDecoder;
import gov.cms.qpp.conversion.stubs.TestDefaultValidator;
import gov.cms.qpp.conversion.validate.QrdaValidator;
import gov.cms.qpp.conversion.xml.XmlUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static gov.cms.qpp.conversion.model.error.ValidationErrorMatcher.hasValidationErrorsIgnoringPath;
import static gov.cms.qpp.conversion.model.error.ValidationErrorMatcher.validationErrorTextMatches;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "org.apache.xerces.*", "javax.xml.parsers.*", "org.xml.sax.*" })
public class ConverterTest {

	@Test(expected = org.junit.Test.None.class)
	public void testValidQppFile() {
		Path path = Paths.get("../qrda-files/valid-QRDA-III-latest.xml");
		Converter converter = new Converter(new PathQrdaSource(path));

		converter.transform();
		//no exception should be thrown, hence explicitly stating the expected exception is None
	}

	@Test(expected = org.junit.Test.None.class)
	public void testValidQppStream() throws IOException {
		Path path = Paths.get("../qrda-files/valid-QRDA-III-latest.xml");
		Converter converter = new Converter(new InputStreamQrdaSource(path.toString(), XmlUtils.fileToStream(path)));

		converter.transform();
		//no exception should be thrown, hence explicitly stating the expected exception is None
	}

	@Test
	@PrepareForTest({Converter.class, QrdaValidator.class})
	public void testValidationErrors() throws Exception {
		Context context = new Context();
		TestHelper.mockDecoder(context, JennyDecoder.class, new ComponentKey(TemplateId.DEFAULT, Program.ALL));
		QrdaValidator mockQrdaValidator = TestHelper.mockValidator(context, TestDefaultValidator.class, new ComponentKey(TemplateId.DEFAULT, Program.ALL), true);
		PowerMockito.whenNew(QrdaValidator.class)
			.withAnyArguments()
			.thenReturn(mockQrdaValidator);

		Path path = Paths.get("src/test/resources/converter/errantDefaultedNode.xml");
		Converter converter = new Converter(new PathQrdaSource(path), context);

		try {
			converter.transform();
			fail("The converter should not create valid QPP JSON");
		} catch (TransformException exception) {
			AllErrors allErrors = exception.getDetails();
			List<Error> errors = allErrors.getErrors();
			assertThat("There must only be one error source.", errors, hasSize(1));
			List<Detail> details = errors.get(0).getDetails();
			assertThat("The expected validation error was missing", details, hasValidationErrorsIgnoringPath("Test validation error for Jenny"));
		}
	}

	@Test
	public void testInvalidXml() throws IOException {
		Path path = Paths.get("src/test/resources/non-xml-file.xml");
		Converter converter = new Converter(new PathQrdaSource(path));

		try {
			converter.transform();
			fail();
		} catch (TransformException exception) {
			checkup(exception, Converter.NOT_VALID_XML_DOCUMENT);
		}
	}

	@Test
	@PrepareForTest({Converter.class, QppOutputEncoder.class})
	public void testEncodingExceptions() throws Exception {
		QppOutputEncoder encoder = mock(QppOutputEncoder.class);
		whenNew(QppOutputEncoder.class).withAnyArguments().thenReturn(encoder);
		EncodeException ex = new EncodeException("mocked", new RuntimeException());
		doThrow(ex).when(encoder).encode();

		Path path = Paths.get("src/test/resources/converter/defaultedNode.xml");
		Converter converter = new Converter(new PathQrdaSource(path));
		converter.getContext().setDoDefaults(false);
		converter.getContext().setDoValidation(false);

		try {
			converter.transform();
			fail();
		} catch (TransformException exception) {
			checkup(exception, Converter.NOT_VALID_XML_DOCUMENT);
		}
	}

	@Test
	public void testInvalidXmlFile() {
		Converter converter = new Converter(new PathQrdaSource(Paths.get("src/test/resources/not-a-QRDA-III-file.xml")));
		
		try {
			converter.transform();
			fail();
		} catch (TransformException exception) {
			checkup(exception, Converter.NOT_VALID_QRDA_DOCUMENT);
		}
	}

	@Test
	public void testNotAValidQrdaIIIFile() throws IOException {
		Path path = Paths.get("src/test/resources/not-a-QRDA-III-file.xml");
		Converter converter = new Converter(new PathQrdaSource(path));
		converter.getContext().setDoDefaults(false);
		converter.getContext().setDoValidation(false);

		try {
			converter.transform();
			fail();
		} catch (TransformException exception) {
			checkup(exception, Converter.NOT_VALID_QRDA_DOCUMENT);
		}
	}

	@Test
	@PrepareForTest({Converter.class, XmlUtils.class})
	public void testUnexpectedError() throws IOException {

		mockStatic(XmlUtils.class);
		when(XmlUtils.fileToStream(any(Path.class))).thenReturn(null);

		Path path = Paths.get("../qrda-files/valid-QRDA-III.xml");
		Converter converter = new Converter(new PathQrdaSource(path));

		try {
			converter.transform();
			fail();
		} catch (TransformException exception) {
			checkup(exception, Converter.UNEXPECTED_ERROR);
		}
	}

	private void checkup(TransformException exception, String errorText) {
		AllErrors allErrors = exception.getDetails();
		List<Error> errors = allErrors.getErrors();
		assertThat("There must only be one error source.", errors, hasSize(1));
		List<Detail> details = errors.get(0).getDetails();
		assertThat("There must be only one validation error.", details, hasSize(1));
		Detail detail = details.get(0);
		assertThat("The validation error was incorrect", detail, validationErrorTextMatches(errorText));
	}

	@Test
	public void testSkipDefaults() throws Exception {
		Converter converter = new Converter(new PathQrdaSource(Paths.get("src/test/resources/converter/defaultedNode.xml")));
		converter.getContext().setDoDefaults(false);
		converter.getContext().setDoValidation(false);
		JsonWrapper qpp = converter.transform();

		String content = qpp.toString();

		assertFalse(content.contains("Jenny"));
	}

	@Test
	public void testDefaults() throws Exception {
		Context context = new Context();
		context.setDoValidation(false);
		TestHelper.mockDecoder(context, JennyDecoder.class, new ComponentKey(TemplateId.DEFAULT, Program.ALL));
		TestHelper.mockEncoder(context, Jenncoder.class, new ComponentKey(TemplateId.DEFAULT, Program.ALL));

		Converter converter = new Converter(new PathQrdaSource(Paths.get("src/test/resources/converter/defaultedNode.xml")), context);
		JsonWrapper qpp = converter.transform();

		String content = qpp.toString();

		assertTrue(content.contains("Jenny"));
	}
}