"""Builds ml/training/dataset.csv: labelled Indian expense notes for the category model (plan 4.1).

Synthetic on purpose: no real labelled notes exist yet. Each category has a word list (English, Hinglish,
Hindi in Devanagari, Indian shop and app names). Short note templates and small typos turn those into the
kind of thing people type ("swiggy dinner", "doodh 2 packet", "bijli ka bill"). Retrain on real notes from
the closed test (5.9) once they exist.

Run:  python ml/training/make_dataset.py      (no packages needed; same output every run)
"""

import csv
import random
from pathlib import Path

SEED = 42
OUT = Path(__file__).with_name("dataset.csv")

# Label "none" = a note that says nothing about the category (names, "paid", "misc"). The app shows no guess.
WORDS = {
    "Groceries": {
        "items": "kirana grocery groceries ration atta aata dal daal chawal rice oil tel masala sugar cheeni namak salt "
        "besan maida suji poha dalia rajma chana moong toor arhar urad ghee butter bread eggs ande biscuits "
        "namkeen maggi noodles soap detergent surf shampoo toothpaste colgate harpic vim tea patti chai patti "
        "coffee powder jaggery gud haldi mirchi jeera sarson tel refined mustard oil cornflakes oats ketchup jam "
        "pickle achaar papad saabun washing powder toilet cleaner phenyl tissue",
        "hindi": "किराना राशन आटा दाल चावल तेल चीनी नमक बेसन मैदा सूजी घी मसाला हल्दी जीरा साबुन बिस्कुट अंडे ब्रेड गुड़",
        "brands": "dmart bigbasket blinkit zepto instamart jiomart reliance fresh more supermarket spencers "
        "easyday vishal mega mart star bazaar nature basket kirana store general store provision store "
        "ratnadeep metro cash carry big bazaar smart bazaar dunzo",
    },
    "Milk": {
        "items": "doodh dudh milk dairy dahi curd paneer chaach buttermilk lassi cream malai milk packet "
        "toned milk full cream milk cow milk buffalo milk bhains doodh gaay doodh milkman doodhwala dudhwala",
        "hindi": "दूध दही पनीर छाछ लस्सी मलाई दूधवाला",
        "brands": "amul mother dairy nandini aavin verka sudha parag gokul heritage country delight "
        "milkbasket akshayakalpa saras banas",
    },
    "Vegetables": {
        "items": "sabzi sabji subzi vegetable vegetables veggies aloo potato pyaz pyaaz onion tamatar tomato "
        "bhindi lauki tori karela gobhi cauliflower patta gobhi cabbage baingan brinjal palak methi dhaniya "
        "coriander mirch green chilli adrak ginger lehsun garlic gajar carrot matar peas shimla mirch capsicum "
        "kheera cucumber mooli radish kaddu pumpkin phal fruits fruit kela banana seb apple santra orange "
        "angoor grapes aam mango papita papaya anaar pomegranate tarbooz watermelon nimbu lemon sabziwala "
        "thela mandi",
        "hindi": "सब्जी सब्ज़ी फल आलू प्याज टमाटर भिंडी लौकी गोभी बैंगन पालक मेथी धनिया मिर्च अदरक लहसुन गाजर मटर "
        "खीरा मूली केला सेब संतरा अंगूर आम पपीता अनार नींबू सब्ज़ीवाला मंडी",
        "brands": "sabzi mandi fruit stall vegetable market kisan market fresh to home farm fresh otipy "
        "kisankonnect vegetable vendor fruit vendor",
    },
    "Rent": {
        "items": "rent kiraya kirayaa house rent flat rent room rent pg rent hostel rent shop rent "
        "landlord makaan malik maintenance society maintenance advance rent security deposit",
        "hindi": "किराया मकान किराया मकान मालिक कमरा किराया",
        "brands": "nobroker nestaway housing society zolo stanza living colive",
    },
    "Electricity": {
        "items": "bijli electricity electric bill light bill power bill current bill meter recharge "
        "prepaid meter bijli bill",
        "hindi": "बिजली बिजली बिल लाइट बिल",
        "brands": "bescom msedcl tata power adani electricity bses torrent power cesc tneb kseb uppcl "
        "pspcl wbsedcl jvvnl dhbvn apspdcl tsspdcl mahavitaran",
    },
    "Water": {
        "items": "paani pani water water bill water can bisleri can water tanker jar water purifier service "
        "ro service drinking water",
        "hindi": "पानी पानी बिल पानी की बोतल टैंकर",
        "brands": "bisleri kinley aquafina bwssb jal board delhi jal board kent ro aquaguard",
    },
    "Gas/LPG": {
        "items": "gas cylinder lpg silender sylinder gas booking gas refill piped gas png cooking gas "
        "chulha gas",
        "hindi": "गैस सिलेंडर गैस सिलेंडर रसोई गैस",
        "brands": "indane bharat gas hp gas mahanagar gas igl adani gas gail gas gujarat gas",
    },
    "Mobile/Internet": {
        "items": "mobile recharge internet wifi broadband data pack data plan postpaid prepaid phone bill "
        "fiber dth tv recharge sim talktime net pack hotspot",
        "hindi": "मोबाइल रिचार्ज इंटरनेट वाईफाई फोन बिल डेटा",
        "brands": "jio airtel vi vodafone idea bsnl act fibernet hathway excitel tata play dish tv "
        "airtel xstream jiofiber sun direct d2h",
    },
    "Maid/Help": {
        "items": "maid bai kaamwali kamwali cook naukrani driver salary maid salary cook salary safai "
        "safaiwali jhadu pocha bartan nanny aaya chowkidar watchman gardener mali dhobi istri ironing press wala "
        "car wash car cleaner",
        "hindi": "बाई कामवाली नौकरानी खाना बनाने वाली ड्राइवर माली धोबी प्रेस वाला चौकीदार",
        "brands": "urban company urbanclap broomees helper4u",
    },
    "Fuel": {
        "items": "petrol diesel fuel cng tank full petrol pump bike petrol car petrol scooty petrol "
        "diesel fill fuel refill",
        "hindi": "पेट्रोल डीजल डीज़ल सीएनजी पेट्रोल पंप",
        "brands": "bpcl hpcl iocl indianoil indian oil bharat petroleum hindustan petroleum shell nayara "
        "essar jio bp",
    },
    "Transport": {
        "items": "auto rickshaw bus metro cab taxi train ticket bus ticket metro card toll parking flight "
        "railway e rickshaw toto shared auto bus pass local train metro recharge car service bike service puncture "
        "tyre servicing",
        "hindi": "ऑटो रिक्शा बस मेट्रो टैक्सी ट्रेन टिकट किराया ऑटो टोल पार्किंग",
        "brands": "ola uber rapido irctc redbus indigo air india spicejet akasa fastag namma yatri "
        "blusmart makemytrip goibibo ixigo abhibus",
    },
    "Food Outside": {
        "items": "restaurant hotel pizza burger chai coffee cafe dinner lunch breakfast biryani samosa momo momos "
        "snacks nashta icecream ice cream dosa idli vada pav pav bhaji chole bhature golgappe pani puri "
        "chaat thali paratha rolls kathi roll shawarma sandwich cake pastry juice cold drink dhaba "
        "bakery sweets eatery canteen tiffin party treat outing food",
        "hindi": "चाय कॉफी नाश्ता खाना रेस्टोरेंट पिज़्ज़ा समोसा ढाबा बिरयानी डोसा चाट गोलगप्पे मिठाई की दुकान",
        "brands": "swiggy zomato dominos pizza hut mcdonalds kfc burger king subway starbucks ccd cafe coffee day "
        "haldiram haldirams bikanervala barbeque nation faasos behrouz chaayos wow momo eatsure box8 "
        "theobroma baskin robbins naturals",
    },
    "Shopping": {
        "items": "shopping kapde clothes shoes joote chappal slipper dress saree kurta shirt jeans tshirt "
        "jacket bag purse watch sunglasses cosmetics makeup lipstick perfume jewellery earrings "
        "mobile cover charger earphones headphones electronics utensils bartan bedsheet curtains "
        "furniture mixer iron household",
        "hindi": "शॉपिंग कपड़े जूते चप्पल साड़ी कुर्ता बैग घड़ी बर्तन चादर",
        "brands": "amazon flipkart myntra ajio meesho nykaa tata cliq snapdeal lifestyle pantaloons "
        "westside max fashion zudio trends shoppers stop decathlon bata ikea croma reliance digital "
        "lenskart firstcry",
    },
    "Medical": {
        "items": "dawai dawa medicine medicines doctor hospital dentist medical chemist tablets injection "
        "pharmacy clinic pathology blood test lab test xray checkup consultation fees doctor fees "
        "operation surgery eye test spectacles chashma vaccine physiotherapy syrup bandage health insurance",
        "hindi": "दवाई दवा डॉक्टर अस्पताल दवाखाना जांच चश्मा इंजेक्शन",
        "brands": "apollo medplus netmeds pharmeasy 1mg tata 1mg practo dr lal pathlabs thyrocare "
        "metropolis wellness forever fortis max hospital manipal aiims",
    },
    "Education": {
        "items": "school fees fee tuition college books kitab stationery coaching exam fees admission "
        "uniform school bag notebook copy pen pencil course online course classes school van school bus "
        "hostel fees tuition teacher library",
        "hindi": "स्कूल फीस किताब ट्यूशन कॉलेज कॉपी पेन कोचिंग परीक्षा",
        "brands": "byjus unacademy vedantu physics wallah allen aakash fiitjee coursera udemy "
        "white hat jr khan academy",
    },
    "Festivals/Gifts": {
        "items": "gift tohfa diwali holi rakhi shaadi wedding birthday puja mithai pataka patakhe crackers "
        "shagun dussehra navratri durga puja eid christmas karwa chauth chhath pongal onam bhai dooj "
        "anniversary return gift flowers decoration diya rangoli prasad mandir donation daan",
        "hindi": "तोहफा दिवाली होली राखी शादी मिठाई पूजा पटाखे शगुन दशहरा नवरात्रि ईद जन्मदिन प्रसाद मंदिर दान",
        "brands": "fnp ferns n petals igp archies haldiram mithai",
    },
    "EMI": {
        "items": "emi kist installment loan emi home loan car loan bike loan personal loan credit card bill "
        "loan repayment monthly installment emi payment",
        "hindi": "ईएमआई किस्त लोन की किस्त",
        "brands": "bajaj finserv hdfc loan sbi loan icici loan home credit tata capital muthoot "
        "manappuram axis loan kotak loan",
    },
    "Entertainment": {
        "items": "movie film cinema picture tickets ott subscription concert game games gaming outing park "
        "amusement park water park zoo museum bowling arcade",
        "hindi": "फिल्म सिनेमा पिक्चर मूवी टिकट मेला",
        "brands": "netflix prime video amazon prime hotstar disney hotstar sonyliv zee5 jiocinema spotify "
        "youtube premium bookmyshow pvr inox cinepolis gaana wonderla imagica",
    },
    "none": {
        "items": "paid given misc miscellaneous other others sent transfer payment kharcha expense money "
        "something odd random stuff thing cash given to friend",
        "hindi": "दिया भेजा खर्चा पैसे बाकी",
        "brands": "ramesh suresh mohan sharma ji gupta ji raju pappu bhaiya didi mummy papa chacha mama "
        "rahul priya amit neha sunita anil",
    },
}

EN_TEMPLATES = [
    "{w}",
    "{w}",
    "{w}",
    "{w} bill",
    "paid {w}",
    "{w} payment",
    "{w} for home",
    "{w} today",
    "{w} ka kharcha",
    "{w} ke liye",
    "{w} liya",
    "{w} diya",
    "{w} {n}",
    "for {w}",
    "{w} and {w2}",
    "{w} {w2}",
    "monthly {w}",
    "{w} this month",
]
HI_TEMPLATES = ["{w}", "{w}", "{w} लिया", "{w} का बिल", "{w} के लिए", "{w} दिया", "{w} {w2}"]
NONE_TEMPLATES = ["{w}", "{w}", "paid {w}", "{w} ko diya", "given to {w}", "{w} {w2}", "{w} ko {w2}"]
NUMBER_WORDS = ["2 kg", "1 packet", "half kg", "5 litre", "do", "teen", "dozen", "2", "500 gm", "big"]
ROWS_PER_CATEGORY = 190


def phrases(text):
    """Splits a word list. Multi-word names are kept whole when they are known pairs (see PAIRS)."""
    words = text.split()
    out, i = [], 0
    while i < len(words):
        pair = " ".join(words[i : i + 2])
        if pair in PAIRS:
            out.append(pair)
            i += 2
        else:
            out.append(words[i])
            i += 1
    return out


PAIRS = {
    "chai patti", "coffee powder", "sarson tel", "mustard oil", "washing powder", "toilet cleaner",
    "milk packet", "toned milk", "full cream", "cow milk", "buffalo milk", "bhains doodh", "gaay doodh",
    "mother dairy", "country delight", "patta gobhi", "green chilli", "shimla mirch", "house rent", "flat rent",
    "room rent", "pg rent", "hostel rent", "shop rent", "makaan malik", "security deposit", "electric bill",
    "light bill", "power bill", "current bill", "tata power", "water bill", "water can", "water tanker",
    "ro service", "jal board", "kent ro", "gas booking", "piped gas", "cooking gas", "bharat gas", "hp gas",
    "mahanagar gas", "data pack", "data plan", "phone bill", "tv recharge", "dish tv", "tata play",
    "maid salary", "cook salary", "jhadu pocha", "urban company", "car wash", "tank full", "petrol pump",
    "indian oil", "bus ticket", "metro card", "local train", "metro recharge", "car service", "bike service", "bus pass", "e rickshaw", "shared auto",
    "air india", "namma yatri", "pizza hut", "burger king", "vada pav", "pav bhaji", "chole bhature",
    "pani puri", "kathi roll", "cold drink", "ice cream", "barbeque nation", "wow momo", "baskin robbins",
    "cafe coffee", "tata cliq", "shoppers stop", "max fashion", "reliance digital", "mobile cover",
    "blood test", "lab test", "eye test", "doctor fees", "health insurance", "dr lal", "tata 1mg",
    "max hospital", "school fees", "exam fees", "school bag", "online course", "school van", "school bus",
    "hostel fees", "physics wallah", "khan academy", "durga puja", "karwa chauth", "bhai dooj",
    "return gift", "ferns n", "home loan", "car loan", "bike loan", "personal loan", "credit card",
    "loan emi", "bajaj finserv", "tata capital", "home credit", "prime video", "amazon prime",
    "disney hotstar", "youtube premium", "amusement park", "water park", "sharma ji", "gupta ji",
    "fruit stall", "vegetable market", "kisan market", "farm fresh", "fruit vendor", "vegetable vendor",
    "sabzi mandi", "jio bp", "star bazaar", "nature basket", "kirana store", "general store",
    "provision store", "big bazaar", "smart bazaar", "jio mart", "reliance fresh", "mega mart",
    "kinley water", "delhi jal", "pathlabs", "wellness forever", "fresh to",
}


def typo(word, rng):
    """One small slip a thumb makes: a letter dropped, doubled or swapped. Latin script only."""
    if len(word) < 5 or not word.isascii():
        return word
    i = rng.randrange(1, len(word) - 1)
    kind = rng.randrange(3)
    if kind == 0:
        return word[:i] + word[i + 1 :]
    if kind == 1:
        return word[:i] + word[i] + word[i:]
    return word[: i - 1] + word[i] + word[i - 1] + word[i + 1 :]


def main():
    rng = random.Random(SEED)
    rows = set()
    for label, lists in WORDS.items():
        latin = phrases(lists["items"]) + phrases(lists["brands"])
        hindi = lists["hindi"].split()
        made = set()
        # Every word on its own at least once, so nothing in the lists is left out.
        for w in latin + hindi:
            made.add(w)
        tries = 0
        while len(made) < ROWS_PER_CATEGORY and tries < ROWS_PER_CATEGORY * 50:
            tries += 1
            if label == "none":
                pool, templates = latin + hindi, NONE_TEMPLATES
            elif rng.random() < 0.2:
                pool, templates = hindi, HI_TEMPLATES
            else:
                pool, templates = latin, EN_TEMPLATES
            w, w2 = rng.choice(pool), rng.choice(pool)
            if rng.random() < 0.15:
                w = typo(w, rng)
            text = rng.choice(templates).format(w=w, w2=w2, n=rng.choice(NUMBER_WORDS))
            made.add(text)
        rows.update((text, label) for text in made)
    with OUT.open("w", newline="", encoding="utf-8") as f:
        out = csv.writer(f)
        out.writerow(["text", "category"])
        for text, label in sorted(rows):
            out.writerow([text, label])
    print(f"{len(rows)} rows -> {OUT}")


if __name__ == "__main__":
    main()
