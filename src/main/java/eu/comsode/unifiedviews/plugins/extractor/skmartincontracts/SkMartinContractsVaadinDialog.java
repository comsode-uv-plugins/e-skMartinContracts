package eu.comsode.unifiedviews.plugins.extractor.skmartincontracts;

import eu.unifiedviews.dpu.config.DPUConfigException;
import eu.unifiedviews.helpers.dpu.vaadin.dialog.AbstractDialog;

/**
 * Vaadin configuration dialog .
 */
public class SkMartinContractsVaadinDialog extends AbstractDialog<SkMartinContractsConfig_V1> {

    public SkMartinContractsVaadinDialog() {
        super(SkMartinContracts.class);
    }

    @Override
    public void setConfiguration(SkMartinContractsConfig_V1 c) throws DPUConfigException {

    }

    @Override
    public SkMartinContractsConfig_V1 getConfiguration() throws DPUConfigException {
        final SkMartinContractsConfig_V1 c = new SkMartinContractsConfig_V1();

        return c;
    }

    @Override
    public void buildDialogLayout() {
    }

}
