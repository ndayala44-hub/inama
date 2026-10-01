const queries = require("../data/queries");

// GET all queries
exports.getQueries = (req, res) => {
  res.status(200).json(queries);
};

// GET single query
exports.getQueryById = (req, res) => {
  const query = queries.find(
    (q) => q.id === parseInt(req.params.id)
  );

  if (!query) {
    return res.status(404).json({
      message: "Query not found",
    });
  }

  res.status(200).json(query);
};

// CREATE query
exports.createQuery = (req, res) => {
  const newQuery = {
    id: queries.length + 1,
    crop: req.body.crop,
    issue: req.body.issue,
    advice: req.body.advice,
  };

  queries.push(newQuery);

  res.status(201).json(newQuery);
};

// PATCH query
exports.patchQuery = (req, res) => {
  const query = queries.find(
    (q) => q.id === parseInt(req.params.id)
  );

  if (!query) {
    return res.status(404).json({
      message: "Query not found",
    });
  }

  if (req.body.crop) query.crop = req.body.crop;
  if (req.body.issue) query.issue = req.body.issue;
  if (req.body.advice) query.advice = req.body.advice;

  res.status(200).json({
    message: "Query updated successfully",
    query,
  });
};

// DELETE query
exports.deleteQuery = (req, res) => {
  const index = queries.findIndex(
    (q) => q.id === parseInt(req.params.id)
  );

  if (index === -1) {
    return res.status(404).json({
      message: "Query not found",
    });
  }

  queries.splice(index, 1);

  res.status(200).json({
    message: "Query deleted successfully",
  });
};

// SEARCH query
exports.searchQuery = (req, res) => {
  const crop = req.query.crop;

  const filteredQueries = queries.filter((q) =>
    q.crop.toLowerCase().includes(crop.toLowerCase())
  );

  res.status(200).json(filteredQueries);
};