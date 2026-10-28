package agent;

import com.bosch.eclipsedriver.agent.core.Labels;
import edtest.Assert;

public class LabelsTest {
    public void testMnemonicAmpersandIsRemoved() {
        Assert.eq("Next >", Labels.clean("&Next >"));
        Assert.eq("Finish", Labels.clean("&Finish"));
    }

    public void testEscapedAmpersandStaysAsOne() {
        Assert.eq("Q&A", Labels.clean("Q&&A"));
    }

    public void testAcceleratorAfterTabIsDropped() {
        Assert.eq("Save", Labels.clean("&Save\tCtrl+S"));
    }

    public void testAcceleratorExtractsTabSuffix() {
        Assert.eq("Ctrl+S", Labels.accelerator("&Save\tCtrl+S"));
        Assert.eq(null, Labels.accelerator("&Save"));
        Assert.eq(null, Labels.accelerator("Save\t  "));
        Assert.eq(null, Labels.accelerator(null));
    }

    public void testTrailingColonAndSpacesAreTrimmedForFieldLabels() {
        Assert.eq("Project name", Labels.field("Project &name:  "));
    }

    public void testNullAndBlankBecomeEmpty() {
        Assert.eq("", Labels.clean(null));
        Assert.eq("", Labels.field("   "));
    }

    public void testConfirmAndDismissRecognition() {
        Assert.isTrue(Labels.isConfirm("OK"), "OK");
        Assert.isTrue(Labels.isConfirm("&Yes"), "Yes");
        Assert.isTrue(Labels.isConfirm("&Open"), "Open");
        Assert.isTrue(Labels.isConfirm("Finish"), "Finish");
        Assert.isTrue(!Labels.isConfirm("Cancel"), "Cancel is not confirm");
        Assert.isTrue(Labels.isDismiss("Cancel"), "Cancel");
        Assert.isTrue(Labels.isDismiss("&Close"), "Close");
        Assert.isTrue(Labels.isDismiss("&No"), "No");
        Assert.isTrue(!Labels.isDismiss("OK"), "OK is not dismiss");
        Assert.isTrue(!Labels.isDismiss("Browse..."), "Browse is not dismiss");
    }

    public void testWizardButtonRecognition() {
        Assert.eq("next", Labels.wizardButton("Next >"));
        Assert.eq("back", Labels.wizardButton("< Back"));
        Assert.eq("finish", Labels.wizardButton("&Finish"));
        Assert.eq("cancel", Labels.wizardButton("Cancel"));
        Assert.eq(null, Labels.wizardButton("Browse..."));
    }
}
