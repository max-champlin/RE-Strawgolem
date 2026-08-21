package org.hero.strawgolem.golem;

import net.minecraft.util.RandomSource;

/**
 * Birth names for golems.
 *
 * <p>Every golem is born a plain harvester - a trade only ever arrives later,
 * with a hat - so the surname is drawn from ONE flat pool rather than a batch
 * matched to its job. That is deliberate on both counts: keying names to the
 * birth class would leave every trade-flavoured surname unreachable, and an
 * occupational surname was never a job description anyway. A man named Smith
 * is not a smith, and a golem named Pickhoe is very probably a harvester.
 *
 * <p>64 first names against 176 surnames gives about 11,000 combinations, so a
 * duplicate in a crew of twenty runs well under 2%.
 */
public final class GolemNames {

    private GolemNames() {}

    private static final String[] FIRST = {
        "Mabel", "Gerald", "Otto", "Hattie", "Cyrus", "Winifred", "Alby", "Nell",
        "Silas", "Prudence", "Barnaby", "Edda", "Rufus", "Clementine", "Wendell",
        "Maud", "Jasper", "Ivy", "Horace", "Bess", "Amos", "Greta", "Cletus",
        "Opal", "Ferris", "Dorcas", "Linus", "Hazel", "Merle", "Tabitha", "Ezra",
        "Fern", "Rowan", "Bertha", "Sol", "Lark", "Bram", "Cordelia", "Ned",
        "Agnes", "Wilbur", "Etta", "Caleb", "Pearl", "Alden", "Rilla", "Gus",
        "Verna", "Elmer", "Sadie", "Tobias", "Birdie", "Nolan", "Cora", "Percy",
        "Elsie", "Mordecai", "Junie", "Abner", "Lettie", "Hollis", "Marge",
        "Quill", "Thea",
    };

    /**
     * One pool, everyone draws from it. Grouped only so the flavours stay easy
     * to read and extend - the grouping has no effect at runtime.
     */
    private static final String[] SURNAMES = {
        // straw and smallholding
        "Thatch", "Bindle", "Strawson", "Haywood", "Bramblewick", "Tuft",
        "Whisker", "Bundle", "Cornsilk", "Rustle", "Fescue", "Pallidge",
        "Meadows", "Hollis", "Barleigh", "Rye", "Millet", "Spelt", "Chandler",
        "Tanner", "Cooper", "Fletcher", "Ashdown", "Marlow", "Pennick", "Quill",
        "Ember", "Cobbleigh",
        // harvest
        "Thatcher", "Wheatley", "Rickwood", "Chaffley", "Sheaf", "Stubble",
        "Garner", "Winnow",
        // craft
        "Wrightson", "Fettle", "Joyner", "Tinker", "Craddock", "Whittle",
        "Bodkin", "Lathe",
        // bees
        "Combe", "Hiveley", "Skepp", "Beeton", "Waxley", "Drone", "Melliss",
        "Apiar",
        // herding
        "Lambwell", "Foldham", "Herdley", "Byrne", "Coteley", "Ewing", "Shepp",
        "Broody",
        // brewing
        "Malt", "Brewster", "Hopwood", "Yeastly", "Tunn", "Barmby", "Wort",
        "Mashley",
        // butchery
        "Cleaver", "Shankly", "Brawn", "Chine", "Trotter", "Marrow", "Sowerby",
        "Gambon",
        // kitchen
        "Ladling", "Brinley", "Pottage", "Skillet", "Trencher", "Sopwell",
        "Batterby", "Roust",
        // digging
        "Digger", "Delving", "Trench", "Spadely", "Hollow", "Burrow", "Scoope",
        "Pitts", "Grubb",
        // fishing
        "Netley", "Creel", "Trawle", "Minnow", "Roe", "Anglin", "Weirley",
        "Gudgeon",
        // gardening
        "Bramble", "Thistle", "Bloom", "Verge", "Mulch", "Sprigg", "Trowell",
        "Hedges",
        // sweeping up
        "Broome", "Swill", "Dustan", "Tidwell", "Mopley", "Scour", "Whisk",
        "Sweepe",
        // timber
        "Timbrell", "Axley", "Hewson", "Barkly", "Sawyer", "Splinter",
        "Boughton", "Fell",
        // metal
        "Rivett", "Bellows", "Forgan", "Tongsley", "Ingot", "Smithwick",
        "Clanton", "Brazier",
        // dairy
        "Churnley", "Curdwell", "Creamer", "Whey", "Pailby", "Rennet",
        "Butterby", "Milburn",
        // mining
        "Pickhoe", "Pickford", "Shafto", "Loam", "Quarrie", "Gritt", "Seamly",
        "Adit", "Stope",
        // fire and forge
        "Cinder", "Emberly", "Slagg", "Bloomer", "Cokely", "Kiln", "Ashby",
        "Crucible",
        // hauling
        "Cartwright", "Baleman", "Crately", "Ledger", "Palletts", "Haulley",
        "Stowe", "Bushell",
    };

    /** Rolls a name. Trade is deliberately not a factor - see the class note. */
    public static String generate(RandomSource random) {
        return FIRST[random.nextInt(FIRST.length)]
                + " " + SURNAMES[random.nextInt(SURNAMES.length)];
    }
}
