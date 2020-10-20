/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.XmrWalletService;
import bisq.core.dao.DaoSetup;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.trade.txproof.xmr.XmrTxProofService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.config.BisqHelpFormatter;
import bisq.common.config.Config;
import bisq.common.config.ConfigException;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.CommonSetup;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.setup.UncaughtExceptionHandler;
import bisq.common.util.Utilities;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class BisqExecutable implements GracefulShutDownHandler, BisqSetup.BisqSetupListener, UncaughtExceptionHandler {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;

    private final String fullName;
    private final String scriptName;
    private final String appName;
    private final String version;

    protected Injector injector;
    protected AppModule module;
    protected Config config;
    private boolean isShutdownInProgress;

    public BisqExecutable(String fullName, String scriptName, String appName, String version) {
        this.fullName = fullName;
        this.scriptName = scriptName;
        this.appName = appName;
        this.version = version;
    }

    public void execute(String[] args) {
        try {
            config = new Config(appName, Utilities.getUserDataDir(), args);
            if (config.helpRequested) {
                config.printHelp(System.out, new BisqHelpFormatter(fullName, scriptName, version));
                System.exit(EXIT_SUCCESS);
            }
        } catch (ConfigException ex) {
            System.err.println("error: " + ex.getMessage());
            System.exit(EXIT_FAILURE);
        } catch (Throwable ex) {
            System.err.println("fault: An unexpected error occurred. " +
                    "Please file a report at https://bisq.network/issues");
            ex.printStackTrace(System.err);
            System.exit(EXIT_FAILURE);
        }

        doExecute();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void doExecute() {
        CommonSetup.setup(config, this);
        CoreSetup.setup(config);

        addCapabilities();

        // If application is JavaFX application we need to wait until it is initialized
        launchApplication();
    }

    protected abstract void configUserThread();

    protected void addCapabilities() {
    }

    // The onApplicationLaunched call must map to UserThread, so that all following methods are running in the
    // thread the application is running and we don't run into thread interference.
    protected abstract void launchApplication();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // If application is a JavaFX application we need wait for onApplicationLaunched
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Headless versions can call inside launchApplication the onApplicationLaunched() manually
    protected void onApplicationLaunched() {
        configUserThread();
        CommonSetup.printSystemLoadPeriodically(10);
        // As the handler method might be overwritten by subclasses and they use the application as handler
        // we need to setup the handler after the application is created.
        CommonSetup.setupUncaughtExceptionHandler(this);
        setupGuice();
        setupAvoidStandbyMode();
        readAllPersisted(this::startApplication);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupGuice() {
        module = getModule();
        injector = getInjector();
        applyInjector();
    }

    protected abstract AppModule getModule();

    protected Injector getInjector() {
        return Guice.createInjector(module);
    }

    protected void applyInjector() {
        // Subclasses might configure classes with the injector here
    }

    protected void readAllPersisted(Runnable completeHandler) {
        readAllPersisted(null, completeHandler);
    }

    protected void readAllPersisted(@Nullable List<PersistedDataHost> additionalHosts, Runnable completeHandler) {
        List<PersistedDataHost> hosts = CorePersistedDataHost.getPersistedDataHosts(injector);
        if (additionalHosts != null) {
            hosts.addAll(additionalHosts);
        }

        AtomicInteger remaining = new AtomicInteger(hosts.size());
        hosts.forEach(e -> {
            new Thread(() -> {
                e.readPersisted();
                remaining.decrementAndGet();
                if (remaining.get() == 0) {
                    UserThread.execute(completeHandler);
                }

            }, "BisqExecutable-read-" + e.getClass().getSimpleName()).start();
        });
    }

    protected void setupAvoidStandbyMode() {
    }

    protected abstract void startApplication();

    // Once the application is ready we get that callback and we start the setup
    protected void onApplicationStarted() {
        runBisqSetup();
    }

    protected void runBisqSetup() {
        BisqSetup bisqSetup = injector.getInstance(BisqSetup.class);
        bisqSetup.addBisqSetupListener(this);
        bisqSetup.start();
    }

    public abstract void onSetupComplete();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GracefulShutDownHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // This might need to be overwritten in case the application is not using all modules
    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.info("Start graceful shutDown");
        if (isShutdownInProgress) {
            return;
        }

        isShutdownInProgress = true;

        if (injector == null) {
            log.info("Shut down called before injector was created");
            resultHandler.handleResult();
            System.exit(EXIT_SUCCESS);
        }

        try {
            injector.getInstance(ArbitratorManager.class).shutDown();
            injector.getInstance(TradeStatisticsManager.class).shutDown();
            injector.getInstance(XmrTxProofService.class).shutDown();
            injector.getInstance(DaoSetup.class).shutDown();
            injector.getInstance(AvoidStandbyModeService.class).shutDown();
            injector.getInstance(XmrWalletService.class).shutDown(); // TODO: why not shut down BtcWalletService, etc?
            injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                log.info("OpenOfferManager shutdown completed");

                injector.getInstance(BtcWalletService.class).shutDown();
                injector.getInstance(BsqWalletService.class).shutDown();

                // We need to shutdown BitcoinJ before the P2PService as it uses Tor.
                WalletsSetup walletsSetup = injector.getInstance(WalletsSetup.class);
                walletsSetup.shutDownComplete.addListener((ov, o, n) -> {
                    log.info("WalletsSetup shutdown completed");

                    injector.getInstance(P2PService.class).shutDown(() -> {
                        log.info("P2PService shutdown completed");
                        module.close(injector);
                        PersistenceManager.flushAllDataToDisk(() -> {
                            log.info("Graceful shutdown completed. Exiting now.");
                            resultHandler.handleResult();
                            System.exit(EXIT_SUCCESS);
                        });
                    });
                });
                walletsSetup.shutDown();

            });

            // Wait max 20 sec.
            UserThread.runAfter(() -> {
                log.warn("Timeout triggered resultHandler");
                PersistenceManager.flushAllDataToDisk(() -> {
                    log.info("Graceful shutdown resulted in a timeout. Exiting now.");
                    resultHandler.handleResult();
                    System.exit(EXIT_SUCCESS);
                });
            }, 20);
        } catch (Throwable t) {
            log.error("App shutdown failed with exception {}", t.toString());
            t.printStackTrace();
            PersistenceManager.flushAllDataToDisk(() -> {
                log.info("Graceful shutdown resulted in an error. Exiting now.");
                resultHandler.handleResult();
                System.exit(EXIT_FAILURE);
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UncaughtExceptionHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        log.error(throwable.toString());

        if (doShutDown)
            gracefulShutDown(() -> log.info("gracefulShutDown complete"));
    }
}
