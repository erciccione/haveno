/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer;

import bisq.common.app.Capabilities;
import bisq.common.util.MathUtils;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.AutoConfirmSettings;
import bisq.core.user.Preferences;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;
import bisq.network.p2p.P2PService;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class holds utility methods for the creation of an Offer.
 * Most of these are extracted here because they are used both in the GUI and in the API.
 * <p>
 * Long-term there could be a GUI-agnostic OfferService which provides these and other functionality to both the
 * GUI and the API.
 */
@Slf4j
public class OfferUtil {

    /**
     * Given the direction, is this a BUY?
     *
     * @param direction the offer direction
     * @return {@code true} for an offer to buy BTC from the taker, {@code false} for an offer to sell BTC to the taker
     */
    public static boolean isBuyOffer(OfferPayload.Direction direction) {
        return direction == OfferPayload.Direction.BUY;
    }

    /**
     * Returns the makerFee as Coin, this can be priced in BTC or BSQ.
     *
     * @param preferences preferences are used to see if the user indicated a preference for paying fees in BTC
     * @param amount      the amount of BTC to trade
     * @return the maker fee for the given trade amount, or {@code null} if the amount is {@code null}
     */
    @Nullable
    public static Coin getMakerFee(Preferences preferences, @Nullable Coin amount) {
//        boolean isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amount);
        boolean isCurrencyForMakerFeeBtc = true; //TODO(niyid) For XMR and not BTC actually. Rename.
        return getMakerFee(true, amount);
    }

    /**
     * Calculates the maker fee for the given amount, marketPrice and marketPriceMargin.
     *
     * @param isCurrencyForMakerFeeBtc {@code true} to pay fee in BTC, {@code false} to pay fee in BSQ
     * @param amount                   the amount of BTC to trade
     * @return the maker fee for the given trade amount, or {@code null} if the amount is {@code null}
     */
    @Nullable
    public static Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, @Nullable Coin amount) {
        if (amount != null) {
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc));
        } else {
            return null;
        }
    }

    /**
     * Checks if the maker fee should be paid in BTC, this can be the case due to user preference or because the user
     * doesn't have enough BSQ.
     *
     * @param preferences preferences are used to see if the user indicated a preference for paying fees in BTC
     * @param amount      the amount of BTC to trade
     * @return {@code true} if BTC is preferred or the trade amount is nonnull and there isn't enough BSQ for it
     */
    public static boolean isCurrencyForMakerFeeBtc(Preferences preferences,
                                                   @Nullable Coin amount) {
//        boolean payFeeInBtc = preferences.getPayFeeInBtc();
//        boolean bsqForFeeAvailable = isBsqForMakerFeeAvailable(amount);
//        return payFeeInBtc || !bsqForFeeAvailable;
        return true; //TODO(niyid) This should always return true since no BSQ and only XMR. Just keeping to minimize code changes
    }

    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param amount the amount of BTC to trade
     * @return {@code true} if the balance is sufficient, {@code false} otherwise
     */
    public static boolean isBsqForMakerFeeAvailable(@Nullable Coin amount) {
//        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
//        Coin makerFee = getMakerFee(false, amount);
//
//        // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
//        // fee each time we open the create offer screen as there the amount is not set.
//        if (makerFee == null)
//            return true;
//
//        Coin surplusFunds = availableBalance.subtract(makerFee);
//        if (Restrictions.isDust(surplusFunds)) {
//            return false; // we can't be left with dust
//        }
//        return !availableBalance.subtract(makerFee).isNegative();
        return false; //TODO(niyid) This should always return false since no BSQ and only XMR. Just keeping to minimize code changes
    }


    @Nullable
    public static Coin getTakerFee(boolean isCurrencyForTakerFeeBtc, @Nullable Coin amount) {
        if (amount != null) {
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    public static boolean isCurrencyForTakerFeeBtc(Preferences preferences,
                                                   Coin amount) {
        boolean payFeeInBtc = preferences.getPayFeeInBtc();
        boolean bsqForFeeAvailable = isBsqForTakerFeeAvailable(amount);
        return payFeeInBtc || !bsqForFeeAvailable;
    }

    public static boolean isBsqForTakerFeeAvailable(@Nullable Coin amount) {
//        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
//        Coin takerFee = getTakerFee(false, amount);
//
//        // If we don't know yet the maker fee (amount is not set) we return true, otherwise we would disable BSQ
//        // fee each time we open the create offer screen as there the amount is not set.
//        if (takerFee == null)
//            return true;
//
//        Coin surplusFunds = availableBalance.subtract(takerFee);
//        if (Restrictions.isDust(surplusFunds)) {
//            return false; // we can't be left with dust
//        }
//        return !availableBalance.subtract(takerFee).isNegative();
        return false; //TODO(niyid) This should always return false since no BSQ. Just keeping to minimize code changes
    }

    public static Volume getRoundedFiatVolume(Volume volumeByAmount) {
        // We want to get rounded to 1 unit of the fiat currency, e.g. 1 EUR.
        return getAdjustedFiatVolume(volumeByAmount, 1);
    }

    public static Volume getAdjustedVolumeForHalCash(Volume volumeByAmount) {
        // EUR has precision 4 and we want multiple of 10 so we divide by 100000 then
        // round and multiply with 10
        return getAdjustedFiatVolume(volumeByAmount, 10);
    }

    /**
     * @param volumeByAmount The volume generated from an amount
     * @param factor         The factor used for rounding. E.g. 1 means rounded to units of 1 EUR, 10 means rounded to 10 EUR...
     * @return The adjusted Fiat volume
     */
    @VisibleForTesting
    static Volume getAdjustedFiatVolume(Volume volumeByAmount, int factor) {
        // Fiat currencies use precision 4 and we want multiple of factor so we divide by 10000 * factor then
        // round and multiply with factor
        long roundedVolume = Math.round((double) volumeByAmount.getValue() / (10000d * factor)) * factor;
        // Smallest allowed volume is factor (e.g. 10 EUR or 1 EUR,...)
        roundedVolume = Math.max(factor, roundedVolume);
        return Volume.parse(String.valueOf(roundedVolume), volumeByAmount.getCurrencyCode());
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount        Bitcoin amount which is a candidate for getting rounded.
     * @param price         Price used in relation to that amount.
     * @param maxTradeLimit The max. trade limit of the users account, in satoshis.
     * @return The adjusted amount
     */
    public static Coin getRoundedFiatAmount(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 1);
    }

    public static Coin getAdjustedAmountForHalCash(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 10);
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount        Bitcoin amount which is a candidate for getting rounded.
     * @param price         Price used in relation to that amount.
     * @param maxTradeLimit The max. trade limit of the users account, in satoshis.
     * @param factor        The factor used for rounding. E.g. 1 means rounded to units of
     *                      1 EUR, 10 means rounded to 10 EUR, etc.
     * @return The adjusted amount
     */
    @VisibleForTesting
    static Coin getAdjustedAmount(Coin amount, Price price, long maxTradeLimit, int factor) {
        checkArgument(
                amount.getValue() >= 10_000,
                "amount needs to be above minimum of 10k satoshis"
        );
        checkArgument(
                factor > 0,
                "factor needs to be positive"
        );
        // Amount must result in a volume of min factor units of the fiat currency, e.g. 1 EUR or
        // 10 EUR in case of HalCash.
        Volume smallestUnitForVolume = Volume.parse(String.valueOf(factor), price.getCurrencyCode());
        if (smallestUnitForVolume.getValue() <= 0)
            return Coin.ZERO;

        Coin smallestUnitForAmount = price.getAmountByVolume(smallestUnitForVolume);
        long minTradeAmount = Restrictions.getMinTradeAmount().value;

        // We use 10 000 satoshi as min allowed amount
        checkArgument(
                minTradeAmount >= 10_000,
                "MinTradeAmount must be at least 10k satoshis"
        );
        smallestUnitForAmount = Coin.valueOf(Math.max(minTradeAmount, smallestUnitForAmount.value));
        // We don't allow smaller amount values than smallestUnitForAmount
        if (amount.compareTo(smallestUnitForAmount) < 0)
            amount = smallestUnitForAmount;

        // We get the adjusted volume from our amount
        Volume volume = getAdjustedFiatVolume(price.getVolumeByAmount(amount), factor);
        if (volume.getValue() <= 0)
            return Coin.ZERO;

        // From that adjusted volume we calculate back the amount. It might be a bit different as
        // the amount used as input before due rounding.
        amount = price.getAmountByVolume(volume);

        // For the amount we allow only 4 decimal places
        long adjustedAmount = Math.round((double) amount.value / 10000d) * 10000;

        // If we are above our trade limit we reduce the amount by the smallestUnitForAmount
        while (adjustedAmount > maxTradeLimit) {
            adjustedAmount -= smallestUnitForAmount.value;
        }
        adjustedAmount = Math.max(minTradeAmount, adjustedAmount);
        adjustedAmount = Math.min(maxTradeLimit, adjustedAmount);
        return Coin.valueOf(adjustedAmount);
    }

    public static Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeBtc,
                                                            Preferences preferences, PriceFeedService priceFeedService,
                                                            CoinFormatter bsqFormatter) {
        String countryCode = preferences.getUserCountry().code;
        String userCurrencyCode = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode();
        return getFeeInUserFiatCurrency(makerFee,
                isCurrencyForMakerFeeBtc,
                userCurrencyCode,
                priceFeedService,
                bsqFormatter);
    }

    private static Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee, boolean isCurrencyForMakerFeeBtc,
                                                             String userCurrencyCode, PriceFeedService priceFeedService,
                                                             CoinFormatter bsqFormatter) {
        // We use the users currency derived from his selected country.
        // We don't use the preferredTradeCurrency from preferences as that can be also set to an altcoin.

        MarketPrice marketPrice = priceFeedService.getMarketPrice(userCurrencyCode);
        if (marketPrice != null && makerFee != null) {
            long marketPriceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(marketPrice.getPrice(), Fiat.SMALLEST_UNIT_EXPONENT));
            Price userCurrencyPrice = Price.valueOf(userCurrencyCode, marketPriceAsLong);

            if (isCurrencyForMakerFeeBtc) {
                return Optional.of(userCurrencyPrice.getVolumeByAmount(makerFee));
            } else {
                Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
                if (optionalBsqPrice.isPresent()) {
                    Price bsqPrice = optionalBsqPrice.get();
                    String inputValue = bsqFormatter.formatCoin(makerFee);
                    Volume makerFeeAsVolume = Volume.parse(inputValue, "BSQ");
                    Coin requiredBtc = bsqPrice.getAmountByVolume(makerFeeAsVolume);
                    return Optional.of(userCurrencyPrice.getVolumeByAmount(requiredBtc));
                } else {
                    return Optional.empty();
                }
            }
        } else {
            return Optional.empty();
        }
    }


    public static Map<String, String> getExtraDataMap(AccountAgeWitnessService accountAgeWitnessService,
                                                      ReferralIdService referralIdService,
                                                      PaymentAccount paymentAccount,
                                                      String currencyCode,
                                                      Preferences preferences,
                                                      OfferPayload.Direction direction) {
        Map<String, String> extraDataMap = new HashMap<>();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String myWitnessHashAsHex = accountAgeWitnessService.getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(OfferPayload.ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        if (referralIdService.getOptionalReferralId().isPresent()) {
            extraDataMap.put(OfferPayload.REFERRAL_ID, referralIdService.getOptionalReferralId().get());
        }

        if (paymentAccount instanceof F2FAccount) {
            extraDataMap.put(OfferPayload.F2F_CITY, ((F2FAccount) paymentAccount).getCity());
            extraDataMap.put(OfferPayload.F2F_EXTRA_INFO, ((F2FAccount) paymentAccount).getExtraInfo());
        }

        extraDataMap.put(OfferPayload.CAPABILITIES, Capabilities.app.toStringList());

        if (currencyCode.equals("XMR") && direction == OfferPayload.Direction.SELL) {
            preferences.getAutoConfirmSettingsList().stream()
                    .filter(e -> e.getCurrencyCode().equals("XMR"))
                    .filter(AutoConfirmSettings::isEnabled)
                    .forEach(e -> extraDataMap.put(OfferPayload.XMR_AUTO_CONF, OfferPayload.XMR_AUTO_CONF_ENABLED_VALUE));
        }

        return extraDataMap.isEmpty() ? null : extraDataMap;
    }

    public static void validateOfferData(FilterManager filterManager,
                                         P2PService p2PService,
                                         double buyerSecurityDeposit,
                                         PaymentAccount paymentAccount,
                                         String currencyCode,
                                         Coin makerFeeAsCoin) {
        checkNotNull(makerFeeAsCoin, "makerFee must not be null");
        checkNotNull(p2PService.getAddress(), "Address must not be null");
        checkArgument(buyerSecurityDeposit <= Restrictions.getMaxBuyerSecurityDepositAsPercent(),
                "securityDeposit must not exceed " +
                        Restrictions.getMaxBuyerSecurityDepositAsPercent());
        checkArgument(buyerSecurityDeposit >= Restrictions.getMinBuyerSecurityDepositAsPercent(),
                "securityDeposit must not be less than " +
                        Restrictions.getMinBuyerSecurityDepositAsPercent());
        checkArgument(!filterManager.isCurrencyBanned(currencyCode),
                Res.get("offerbook.warning.currencyBanned"));
        checkArgument(!filterManager.isPaymentMethodBanned(paymentAccount.getPaymentMethod()),
                Res.get("offerbook.warning.paymentMethodBanned"));
    }

    // TODO no code duplication found in UI code (added for API)
   /* public static Coin getFundsNeededForOffer(Coin tradeAmount, Coin buyerSecurityDeposit, OfferPayload.Direction direction) {
        boolean buyOffer = isBuyOffer(direction);
        Coin needed = buyOffer ? buyerSecurityDeposit : Restrictions.getSellerSecurityDeposit();
        if (!buyOffer)
            needed = needed.add(tradeAmount);

        return needed;
    }*/
}
