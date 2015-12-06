# Attentions

Attentions budgets your Twitter timeline. Made during Clojure Cup 2015. Live at http://attentions.oskarth.com/

Attempt to make a better timeline and to solve the Twitter Attention Inequality problem. Also uses a user's favorite count to influence their tweets relevance score.

> Twitter Attention Inequality: A person tweeting 100 times a day gets 100 times more exposure than someone tweeting once a day, even though you care equally about what they have to say.

Continuation of http://experiments.oskarth.com/how-to-follow-pmarca/

# Development

`boot dev` and go to ``http://localhost:3000``. You need to register a Twitter App and put the consumer key/token in ``resources/secrets.edn``.

# Software

- Clojure & Clojurescript
- boot
- clj-oauth
- re-frame
- Twitter API
