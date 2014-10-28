package hudson.plugins.emailext;

import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.plugins.emailext.plugins.trigger.FailureTrigger;
import hudson.plugins.emailext.plugins.trigger.StillFailingTrigger;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import net.sf.json.JSONObject;


import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.*;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.mock_javamail.Mailbox;

public class ExtendedEmailPublisherTest
    extends BaseEmailTest
{
    public void testShouldSendEmailUsingUtf8ByDefault()
        throws Exception
    {
        project.getBuildersList().add( new FailureBuilder() );

        FailureTrigger trigger = new FailureTrigger();
        addEmailType( trigger );
        publisher.getConfiguredTriggers().add( trigger );

        FreeStyleBuild build = project.scheduleBuild2( 0 ).get();
        assertBuildStatus( Result.FAILURE, build );

        Mailbox mailbox = Mailbox.get( "ashlux@gmail.com" );
        assertEquals( "We should an email since the build failed.", 1, mailbox.size() );
        String contentType = mailbox.get( 0 ).getContentType();
        if (contentType.startsWith("multipart/mixed")) {
            Multipart multipart = (Multipart) mailbox.get( 0 ).getContent();
            MimeBodyPart bodypart = (MimeBodyPart) multipart.getBodyPart(0);
            contentType = bodypart.getContentType();
        }
        assertThat( "UTF-8 charset should be used.", contentType,
                    containsString( "charset=utf-8" ) );
    }

    public void testNewInstance_shouldGetBasicInformation()
        throws Exception
    {
        JSONObject form = new JSONObject();
        form.put( "project_content_type", "default" );
        form.put( "recipientlist_recipients", "ashlux@gmail.com" );
        form.put( "project_default_subject", "Make millions in Nigeria" );
        form.put( "project_default_content", "Give me a $1000 check and I'll mail you back $5000!!!" );
        form.put( "project_attachments", "foo.*" );
        form.put( "project_attach_buildlog", "0" );

        publisher = (ExtendedEmailPublisher) ExtendedEmailPublisher.DESCRIPTOR.newInstance( null, form );

        assertEquals( "default", publisher.contentType );
        assertEquals( "ashlux@gmail.com", publisher.recipientList );
        assertEquals( "Make millions in Nigeria", publisher.defaultSubject );
        assertEquals( "Give me a $1000 check and I'll mail you back $5000!!!", publisher.defaultContent );
    }

    public void testStillFailingTriggerShouldNotSendEmailWhenBuildIsFixed()
        throws Exception
    {
        project.getBuildersList().add( new FailureBuilder() );

        StillFailingTrigger trigger = new StillFailingTrigger();
        addEmailType( trigger );
        publisher.getConfiguredTriggers().add( trigger );

        // only fail once
        FreeStyleBuild build1 = project.scheduleBuild2( 0 ).get();
        assertBuildStatus( Result.FAILURE, build1 );
        // then succeed
        project.getBuildersList().clear();
        FreeStyleBuild build2 = project.scheduleBuild2( 0 ).get();
        assertBuildStatusSuccess( build2 );

        assertThat( "Email should not have been triggered, so we should not see it in the logs.", build2.getLog( 100 ),
                    not( hasItems( "Email was triggered for: " + StillFailingTrigger.TRIGGER_NAME ) ) );
        assertEquals( 0, Mailbox.get( "ashlux@gmail.com" ).size() );
    }

    public void testStillFailingTriggerShouldSendEmailWhenBuildContinuesToFail()
        throws Exception
    {
        project.getBuildersList().add( new FailureBuilder() );

        StillFailingTrigger trigger = new StillFailingTrigger();
        addEmailType( trigger );
        publisher.getConfiguredTriggers().add( trigger );

        // first failure
        FreeStyleBuild build1 = project.scheduleBuild2( 0 ).get();
        assertBuildStatus( Result.FAILURE, build1 );
        // second failure
        FreeStyleBuild build2 = project.scheduleBuild2( 0 ).get();
        assertBuildStatus( Result.FAILURE, build2 );

        assertThat( "Email should have been triggered, so we should see it in the logs.", build2.getLog( 100 ),
                    hasItems( "Email was triggered for: " + StillFailingTrigger.TRIGGER_NAME ) );
        assertEquals( "We should only have one email since the first failure doesn't count as 'still failing'.", 1,
                      Mailbox.get( "ashlux@gmail.com" ).size() );
    }
}
