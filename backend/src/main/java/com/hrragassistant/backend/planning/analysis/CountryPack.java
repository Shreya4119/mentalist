package com.hrragassistant.backend.planning.analysis;

import java.util.List;
import java.util.Map;

import com.hrragassistant.backend.planning.analysis.HouseholdInput.Country;

/**
 * Country-specific rules the household check needs. Ratio formulas and
 * thresholds are the same everywhere; a pack supplies local names for the
 * insurance covers, amounts the emergency fund must also absorb, tax notes
 * and their sources. Values carry the date they were last verified and must
 * be re-checked against official sources before going live.
 */
public record CountryPack(
        Country country,
        String countryName,
        String currency,
        Map<String, String> insuranceNames,
        double healthOwnRiskPerAdult,
        String healthOwnRiskNote,
        double investmentTaxFreeAmountPerAdult,
        String verifiedOn,
        List<Source> sources) {

    public record Source(String label, String url) {
    }

    public static final String VERIFIED_ON = "2026-10-14";

    public static CountryPack of(Country country) {
        return switch (country) {
            case DE -> new CountryPack(
                    Country.DE, "Germany", "EUR",
                    Map.of(
                            "liability", "Private liability insurance (Privathaftpflicht)",
                            "health", "Health insurance (statutory or private)",
                            "disability", "Occupational disability insurance (Berufsunfähigkeitsversicherung)",
                            "termLife", "Term life insurance (Risikolebensversicherung)"),
                    0, null,
                    1000,
                    VERIFIED_ON,
                    List.of(new Source("Investment taxes in Germany", "https://quantroutine.com/learn/investing-taxes-germany/")));
            case NL -> new CountryPack(
                    Country.NL, "Netherlands", "EUR",
                    Map.of(
                            "liability", "Liability insurance (aansprakelijkheidsverzekering, AVP)",
                            "health", "Basic health insurance (basisverzekering)",
                            "disability", "Disability insurance (arbeidsongeschiktheidsverzekering)",
                            "termLife", "Term life insurance (overlijdensrisicoverzekering)"),
                    385, "the mandatory health-insurance own risk (eigen risico): €385 in 2026, €400 in 2027",
                    59357,
                    VERIFIED_ON,
                    List.of(
                            new Source("Box 3 and investing in the Netherlands (2026)", "https://quantroutine.com/learn/investing-taxes-netherlands/"),
                            new Source("Box 3 actual-return bill: status", "https://hbnlawtax.com/tax-instant-news/box-3-update-senate-review-and-practical-implementation-of-actual-return-relief/"),
                            new Source("Mandatory own risk 2026–2027", "https://www.consumentenbond.nl/zorgverzekering/verplicht-eigen-risico")));
        };
    }

    /** Extra cash the emergency fund should hold on top of N months of essential spending. */
    public double emergencyExtra(int adults) {
        return healthOwnRiskPerAdult * adults;
    }

    /** Plain-language notes for this household, each tied to a source. */
    public List<CountryNote> notes(HouseholdInput in) {
        int adults = in.adultsOrOne();
        double wealth = HouseholdInput.orZero(in.cashSavings()) + HouseholdInput.orZero(in.investmentsTotal());
        String forWhom = in.isIndividual() ? "for one person" : (adults == 2 ? "for your household (two adults)" : "for your household");
        return switch (country) {
            case DE -> List.of(
                    new CountryNote("tax-allowance", "Tax-free investment income",
                            "In Germany the first €1,000 of investment income a year is tax-free (€2,000 for a couple assessed jointly); "
                                    + "above that a flat tax of about 26.4% applies. Give your bank an exemption order (Freistellungsauftrag) "
                                    + "so the allowance is used automatically.",
                            sources.get(0)),
                    new CountryNote("liability-de", "Liability insurance",
                            "Private liability insurance is not compulsory in Germany but is cheap and covers damage you cause to others, "
                                    + "which can otherwise reach life-changing amounts.",
                            null));
            case NL -> {
                double allowance = investmentTaxFreeAmountPerAdult * adults;
                String box3 = wealth > allowance
                        ? "Your savings and investments (" + Money.eur(wealth) + ") are above the Box 3 tax-free amount " + forWhom + " ("
                                + Money.eur(allowance) + "). Box 3 taxes an assumed return, not what you actually earned: for 2026 "
                                + "1.28% on savings and 6.00% on investments, taxed at 36%. A tax on actual returns is planned for 2028 but is not law yet."
                        : "Your savings and investments (" + Money.eur(wealth) + ") are below the Box 3 tax-free amount " + forWhom + " ("
                                + Money.eur(allowance) + "), so no Box 3 tax is due on them. Above it, Box 3 taxes an assumed return "
                                + "(2026: 1.28% on savings, 6.00% on investments) at 36%.";
                yield List.of(
                        new CountryNote("box3", "Box 3 wealth tax", box3, sources.get(0)),
                        new CountryNote("own-risk", "Health-insurance own risk",
                                "Keep " + healthOwnRiskNote + " per adult in your emergency fund on top of your monthly costs.",
                                sources.get(2)));
            }
        };
    }

    public record CountryNote(String key, String title, String text, Source source) {
    }
}
