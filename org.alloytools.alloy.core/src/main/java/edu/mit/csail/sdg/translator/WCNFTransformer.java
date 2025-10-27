package edu.mit.csail.sdg.translator;

import aQute.bnd.annotation.spi.ServiceProvider;
import kodkod.engine.satlab.SATFactory;

@ServiceProvider(SATFactory.class )
public class WCNFTransformer extends SATFactory {

    private static final long serialVersionUID = 1L;

    @Override
    public String id() {
        return "WCNF output";
    }

    @Override
    public boolean maxsat() {
        return true;
    }

    @Override
    public String type() {
        return "transformer";
    }
    
    @Override
    public boolean isTransformer() {
        return true;
    }
    
    @Override
    public boolean incremental() {
        return true;
    }

    @Override
    public boolean isPresent() {
        return true;
    }     
}
