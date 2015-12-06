# Attentions

Attentions budgets your Twitter timeline. Made during Clojure Cup 2015. Live at http://attentions.oskarth.com/

Attempt to make a better timeline and to solve the Twitter Attention Inequality problem. Also uses a user's favorite count to influence their tweets relevance score.

> Twitter Attention Inequality: A person tweeting 100 times a day gets 100 times more exposure than someone tweeting once a day, even though you care equally about what they have to say.

Continuation of http://experiments.oskarth.com/how-to-follow-pmarca/

# Algorithm

To determine a tweet's relevance, we currently only look at a person's posting
frequency in the latest 200 tweets, and the logged-in user's favorite count of a
given person. Here are some example to give you an idea how it works:

`
1. FREQ 5 FAV 1 REL :ok
2. FREQ 2 FAV 12 REL :amazing
3. FREQ 17 FAV 12 :good
`

In the first example, the person has tweeted 5 times and the logged-in user has
only favorited 1 tweet. It's thus not a very high-relevant tweeter. On the other
hand, in the second example, we see that the person hasn't posted a lot but it's
had its tweets liked a lot, which indicates that this is a person we don't want
to miss. The third poster is a bit in the middle, because they post quite a lot
so we might now want to see everything.

At present time each relevance rating is a function only of the person tweeting,
but it's applied on a per-tweet basis. The relevance determines the chance of
seeing the tweet: amazing is 100%, great 75%, good 50% and ok 25% likely to show
up in the final timeline.

Further enhancements would include:

- ability to titrate individual users
- consider total tweet volume, not just last batch
- analyze relevance on a topic-basis, for example by separating programming tweets from politics tweets with tf-idf analysis

# Development

`boot dev` and go to ``http://localhost:3000``. You need to register a Twitter App and put the consumer key/token in ``resources/secrets.edn``.

# Software

- Clojure & Clojurescript
- boot
- clj-oauth
- re-frame
- Twitter API
